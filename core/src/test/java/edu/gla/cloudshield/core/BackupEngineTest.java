package edu.gla.cloudshield.core;

import static org.junit.jupiter.api.Assertions.*;

import edu.gla.cloudshield.core.Models.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class BackupEngineTest {
  @TempDir Path temp;
  RuntimeContext r;

  @BeforeEach
  void setup() {
    r = RuntimeContext.create(Config.from(Map.of("CLOUDSHIELD_DATA_DIR", temp.toString())));
    SampleData.seed(r.source());
  }

  Job run(String type, String backup) {
    return r.engine().execute(r.engine().createJob(type, backup, "TEST_SUITE").id);
  }

  String backup() {
    Job j = run("BACKUP", null);
    assertEquals("SUCCEEDED", j.status, j.message);
    return j.backupId;
  }

  BackupEngine engine(
      BlobStore source, BlobStore primary, BlobStore secondary, BlobStore recovery) {
    return new BackupEngine(
        r.config(),
        source,
        primary,
        secondary,
        recovery,
        r.repository(),
        new Alerts(r.config(), null));
  }

  Job runEngine(BackupEngine engine, String type, String id) {
    return engine.execute(engine.createJob(type, id, "TEST_SUITE").id);
  }

  @Test
  void backupHasVerifiedIndependentRegionalCopies() {
    String id = backup();
    Backup b = r.repository().backup(id).orElseThrow();
    assertEquals("REPLICATED", b.status);
    assertEquals(3, b.objectCount);
    for (var object : r.primary().list("backups/" + id + "/"))
      assertArrayEquals(r.primary().read(object.key()), r.secondary().read(object.key()));
  }

  @Test
  void testMeasuresChangesAndDeletesTemporaryEnvironment() {
    String id = backup();
    SampleData.addRegistration(r.source());
    Job j = run("TEST", id);
    assertEquals("SUCCEEDED", j.status, j.message);
    assertEquals(1, j.changedObjects);
    assertTrue(j.changedBytes > 0);
    assertEquals(0, j.deletedObjects);
    assertEquals(3, j.verifiedObjects);
    assertTrue(j.rtoMillis > 0);
    assertTrue(j.rpoSeconds >= 0);
    assertEquals("CLEANED", j.cleanupStatus);
    assertTrue(r.recovery().list("tests/").isEmpty());
  }

  @Test
  void restoreRetainsOlderDataWithoutOverwritingSource() {
    String id = backup();
    byte[] original = r.source().read("application/registrations.json");
    SampleData.addRegistration(r.source());
    Job j = run("RESTORE", id);
    assertEquals("SUCCEEDED", j.status, j.message);
    assertEquals("RETAINED", j.cleanupStatus);
    assertArrayEquals(
        original, r.recovery().read(j.restorePrefix + "application/registrations.json"));
    assertFalse(Arrays.equals(original, r.source().read("application/registrations.json")));
  }

  @Test
  void corruptPrimaryFallsBackToSecondary() {
    String id = backup();
    r.primary()
        .write("backups/" + id + "/files/application/registrations.json", "corrupt".getBytes());
    Job j = run("TEST", id);
    assertEquals("SUCCEEDED", j.status, j.message);
    assertEquals(r.config().secondaryRegion(), j.regionUsed);
  }

  @Test
  void unavailablePrimaryFallsBackToSecondary() {
    String id = backup();
    var failing =
        new ProxyStore(r.primary()) {
          @Override
          public byte[] read(String k, String v) {
            throw new IllegalStateException("Primary unavailable");
          }
        };
    Job j = runEngine(engine(r.source(), failing, r.secondary(), r.recovery()), "TEST", id);
    assertEquals("SUCCEEDED", j.status);
    assertEquals(r.config().secondaryRegion(), j.regionUsed);
  }

  @Test
  void twoCorruptCopiesFailAndStillCleanUp() {
    String id = backup();
    String key = "backups/" + id + "/files/application/catalog.json";
    r.primary().write(key, "corrupt".getBytes());
    r.secondary().write(key, "corrupt".getBytes());
    Job j = run("TEST", id);
    assertEquals("FAILED", j.status);
    assertTrue(j.message.contains("Neither backup copy"));
    assertEquals("CLEANED", j.cleanupStatus);
    assertTrue(r.recovery().list("tests/").isEmpty());
  }

  @Test
  void invalidApplicationJsonIsDetectedAndRemoved() {
    r.source().write("application/catalog.json", "{bad json".getBytes());
    String id = backup();
    Job j = run("TEST", id);
    assertEquals("FAILED", j.status);
    assertTrue(j.message.contains("JSON is invalid"));
    assertEquals("CLEANED", j.cleanupStatus);
    assertTrue(r.recovery().list("tests/").isEmpty());
  }

  @Test
  void invalidApplicationSchemaIsDetected() {
    r.source()
        .write(
            "application/registrations.json",
            "{\"schemaVersion\":2,\"registrations\":[]}".getBytes());
    Job j = run("TEST", backup());
    assertEquals("FAILED", j.status);
    assertTrue(j.message.contains("schema health"));
    assertEquals("CLEANED", j.cleanupStatus);
  }

  @Test
  void cleanupFailureCannotBeReportedAsSuccessful() {
    String id = backup();
    var failing =
        new ProxyStore(r.recovery()) {
          @Override
          public void deletePrefix(String p) {
            throw new IllegalStateException("Delete denied");
          }
        };
    Job j = runEngine(engine(r.source(), r.primary(), r.secondary(), failing), "TEST", id);
    assertEquals("FAILED", j.status);
    assertEquals("FAILED", j.cleanupStatus);
    assertTrue(j.message.contains("cleanup failed"));
  }

  @Test
  void partialWriteFailureIsCleaned() {
    String id = backup();
    var failing =
        new ProxyStore(r.recovery()) {
          int writes;

          @Override
          public void write(String k, byte[] b) {
            if (++writes == 2) throw new IllegalStateException("Write failed");
            super.write(k, b);
          }
        };
    Job j = runEngine(engine(r.source(), r.primary(), r.secondary(), failing), "TEST", id);
    assertEquals("FAILED", j.status);
    assertTrue(r.recovery().list("tests/").isEmpty());
    assertEquals("CLEANED", j.cleanupStatus);
  }

  @Test
  void replicationFailurePreservesUsablePrimary() {
    var failing =
        new ProxyStore(r.secondary()) {
          @Override
          public void write(String k, byte[] b) {
            throw new IllegalStateException("Secondary unavailable");
          }
        };
    Job b = runEngine(engine(r.source(), r.primary(), failing, r.recovery()), "BACKUP", null);
    assertEquals("FAILED", b.status);
    assertEquals("PRIMARY_ONLY", r.repository().backup(b.backupId).orElseThrow().status);
    assertEquals("SUCCEEDED", run("TEST", b.backupId).status);
  }

  @Test
  void manifestTamperingCannotBypassChecksum() {
    String id = backup();
    r.primary().write("backups/" + id + "/manifest.json", "{}".getBytes());
    Job j = run("TEST", id);
    assertEquals("SUCCEEDED", j.status);
    assertEquals(r.config().secondaryRegion(), j.regionUsed);
  }

  @Test
  void unavailableSourceDoesNotInventDataLossMetrics() {
    String id = backup();
    var unavailable =
        new ProxyStore(r.source()) {
          @Override
          public List<Blob> list(String p) {
            throw new IllegalStateException("Source down");
          }
        };
    Job j = runEngine(engine(unavailable, r.primary(), r.secondary(), r.recovery()), "TEST", id);
    assertEquals("SUCCEEDED", j.status);
    assertNull(j.changedObjects);
    assertNull(j.changedBytes);
    assertTrue(j.message.contains("unavailable"));
  }

  @Test
  void emptySourceIsNotAcceptedAsBackup() {
    r.source().deletePrefix("application/");
    Job j = run("BACKUP", null);
    assertEquals("FAILED", j.status);
    assertTrue(r.repository().backups().isEmpty());
  }

  @Test
  void byteLimitRejectsBeforeBackupWrites() {
    Config c = Config.from(Map.of("CLOUDSHIELD_DATA_DIR", temp.toString(), "MAX_BYTES", "1"));
    var engine =
        new BackupEngine(
            c,
            r.source(),
            r.primary(),
            r.secondary(),
            r.recovery(),
            r.repository(),
            new Alerts(c, null));
    assertEquals("FAILED", runEngine(engine, "BACKUP", null).status);
    assertTrue(r.primary().list("").isEmpty());
  }

  @Test
  void completedJobIsIdempotent() {
    String id = backup();
    Job original = r.repository().job(id).orElseThrow();
    Job repeated = r.engine().execute(id);
    assertEquals(original.finishedAt, repeated.finishedAt);
    assertEquals(1, r.repository().backups().size());
  }

  @Test
  void deterministicScheduleIdDoesNotCreateOrphanJobs() {
    String stable = "20261112T020000000Z-abcd1234";
    Job first = r.engine().createJob("BACKUP", null, "SCHEDULE", stable);
    Job second = r.engine().createJob("BACKUP", null, "SCHEDULE", stable);
    assertEquals(first.id, second.id);
    assertEquals(1, r.repository().jobs().size());
  }

  @Test
  void metadataSurvivesRepositoryRecreation() {
    String id = backup();
    var reloaded = new LocalRepository(temp.resolve("metadata"));
    assertEquals("REPLICATED", reloaded.backup(id).orElseThrow().status);
    assertEquals("SUCCEEDED", reloaded.job(id).orElseThrow().status);
  }

  @Test
  void leasePreventsOverlappingWorkers() {
    var other = new LocalRepository(temp.resolve("metadata"));
    assertTrue(r.repository().acquire("one"));
    assertFalse(other.acquire("two"));
    r.repository().release("one");
    assertTrue(other.acquire("two"));
    other.release("two");
  }

  @Test
  void traversalAndEmptyDeletionAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> r.source().write("../escape.txt", new byte[1]));
    assertThrows(
        IllegalArgumentException.class, () -> r.source().write("C:\\escape.txt", new byte[1]));
    assertThrows(IllegalArgumentException.class, () -> r.source().deletePrefix(""));
    assertThrows(IllegalArgumentException.class, () -> r.repository().job("../escape"));
  }

  @Test
  void nonexistentBackupAndUnknownOperationsAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> r.engine().createJob("RESTORE", "missing", "TEST"));
    assertThrows(IllegalArgumentException.class, () -> r.engine().createJob(null, null, "TEST"));
    assertEquals("FAILED", run("TEST", null).status);
  }

  @Test
  void azureRequiresDistinctRegions() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Config.from(
                Map.of(
                    "CLOUDSHIELD_MODE",
                    "azure",
                    "AZURE_PRIMARY_REGION",
                    "centralindia",
                    "AZURE_SECONDARY_REGION",
                    "centralindia")));
  }

  static class ProxyStore implements BlobStore {
    final BlobStore delegate;

    ProxyStore(BlobStore delegate) {
      this.delegate = delegate;
    }

    public List<Blob> list(String p) {
      return delegate.list(p);
    }

    public byte[] read(String k, String v) {
      return delegate.read(k, v);
    }

    public void write(String k, byte[] b) {
      delegate.write(k, b);
    }

    public void deletePrefix(String p) {
      delegate.deletePrefix(p);
    }
  }
}
