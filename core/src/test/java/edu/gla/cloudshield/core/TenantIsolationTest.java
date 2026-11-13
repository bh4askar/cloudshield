package edu.gla.cloudshield.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantIsolationTest {
  @TempDir Path temp;

  @Test
  void tenantObjectsMetadataAndRecoveryAreIndependent() {
    RuntimeContext base =
        RuntimeContext.create(Config.from(Map.of("CLOUDSHIELD_DATA_DIR", temp.toString())));
    RuntimeContext alice = base.forTenant("a".repeat(32)), bob = base.forTenant("b".repeat(32));
    SampleData.seed(alice.source());
    SampleData.seed(bob.source());
    var backup = alice.engine().execute(alice.engine().createJob("BACKUP", null, "TEST").id);
    assertEquals("SUCCEEDED", backup.status);
    assertTrue(bob.repository().backups().isEmpty());
    assertTrue(bob.repository().job(backup.id).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> bob.engine().createJob("RESTORE", backup.backupId, "TEST"));
    SampleData.addRegistration(alice.source());
    assertNotEquals(
        BackupEngine.sha(alice.source().read("application/registrations.json")),
        BackupEngine.sha(bob.source().read("application/registrations.json")));
    var restored =
        alice.engine().execute(alice.engine().createJob("RESTORE", backup.backupId, "TEST").id);
    assertEquals("SUCCEEDED", restored.status);
    assertTrue(bob.recovery().list("restores/").isEmpty());
    assertThrows(
        IllegalStateException.class,
        () -> bob.recovery().read(restored.restorePrefix + "application/registrations.json"));
  }

  @Test
  void traversalCannotEscapePrefixEvenWithCloudStore() {
    RuntimeContext base =
        RuntimeContext.create(Config.from(Map.of("CLOUDSHIELD_DATA_DIR", temp.toString())));
    BlobStore scoped = new PrefixBlobStore(base.source(), "a".repeat(32));
    assertThrows(
        IllegalArgumentException.class,
        () -> scoped.read("../" + "b".repeat(32) + "/application/registrations.json"));
    assertThrows(IllegalArgumentException.class, () -> scoped.list("../../"));
    assertThrows(IllegalArgumentException.class, () -> scoped.deletePrefix(""));
    assertThrows(IllegalArgumentException.class, () -> base.forTenant("../../demo"));
  }

  @Test
  void setupCreatesPrivateSampleAndFirstBackup() {
    RuntimeContext runtime =
        RuntimeContext.create(Config.from(Map.of("CLOUDSHIELD_DATA_DIR", temp.toString())))
            .forTenant("c".repeat(32));
    var job = runtime.engine().execute(runtime.engine().createJob("SETUP", null, "ONBOARDING").id);
    assertEquals("SUCCEEDED", job.status, job.message);
    assertEquals(3, runtime.source().list("application/").size());
    assertEquals(1, runtime.repository().backups().size());
  }
}
