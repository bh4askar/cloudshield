package edu.gla.cloudshield.core;

import static org.junit.jupiter.api.Assertions.*;

import edu.gla.cloudshield.core.Models.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in contract tests against real Azure, using a new disposable tenant for each test. */
@EnabledIfEnvironmentVariable(named = "CLOUDSHIELD_AZURE_TESTS", matches = "true")
class AzureIntegrationTest {
  RuntimeContext r;

  @BeforeEach
  void setup() {
    r =
        RuntimeContext.create(Config.fromEnv())
            .forTenant(UUID.randomUUID().toString().replace("-", ""));
    SampleData.seed(r.source());
  }

  @Test
  void backupRestoreAndSecondaryFallback() {
    Job backup = r.engine().execute(r.engine().createJob("BACKUP", null, "AZURE_TEST").id);
    assertEquals("SUCCEEDED", backup.status, backup.message);
    assertEquals("REPLICATED", r.repository().backup(backup.backupId).orElseThrow().status);
    byte[] original = r.source().read("application/registrations.json");
    // This backup belongs only to this disposable test workspace.
    r.primary()
        .write(
            "backups/" + backup.backupId + "/files/application/registrations.json",
            "corrupt".getBytes());
    SampleData.addRegistration(r.source());
    Job test = r.engine().execute(r.engine().createJob("TEST", backup.backupId, "AZURE_TEST").id);
    assertEquals("SUCCEEDED", test.status, test.message);
    assertEquals("CLEANED", test.cleanupStatus);
    assertEquals(r.config().secondaryRegion(), test.regionUsed);
    assertEquals(1, test.changedObjects);
    assertTrue(r.recovery().list(test.restorePrefix).isEmpty());
    Job restore =
        r.engine().execute(r.engine().createJob("RESTORE", backup.backupId, "AZURE_TEST").id);
    assertEquals("SUCCEEDED", restore.status, restore.message);
    assertArrayEquals(
        original, r.recovery().read(restore.restorePrefix + "application/registrations.json"));
    assertFalse(Arrays.equals(original, r.source().read("application/registrations.json")));
  }

  @Test
  void sourceVersionAndBoundedReads() {
    var before =
        r.source().list("application/").stream()
            .filter(b -> b.key().endsWith("registrations.json"))
            .findFirst()
            .orElseThrow();
    byte[] bytes = r.source().read(before.key(), before.version());
    SampleData.addRegistration(r.source());
    assertArrayEquals(bytes, r.source().read(before.key(), before.version()));
    assertThrows(IllegalArgumentException.class, () -> r.recovery().deletePrefix(""));
  }

  @Test
  void metadataIsolationOrderingAndDuplicateCreate() {
    var other =
        RuntimeContext.create(Config.fromEnv())
            .forTenant(UUID.randomUUID().toString().replace("-", ""));
    String old = "20260101T000000000Z-11111111", recent = "20261113T000000000Z-22222222";
    r.engine().createJob("BACKUP", null, "AZURE_TEST", old);
    Job j = r.engine().createJob("BACKUP", null, "AZURE_TEST", recent);
    j.status = "SUCCEEDED";
    r.repository().save(j);
    assertEquals("SUCCEEDED", r.engine().createJob("BACKUP", null, "AZURE_TEST", recent).status);
    assertEquals(recent, r.repository().jobs().getFirst().id);
    assertTrue(other.repository().job(recent).isEmpty());
    assertTrue(other.source().list("application/").isEmpty());
  }

  @Test
  void atomicLeaseAndWrongOwnerRelease() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Callable<Boolean>> tasks = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        final String owner = "owner" + i;
        tasks.add(() -> r.repository().acquire(owner));
      }
      var results = pool.invokeAll(tasks);
      int winner = -1, count = 0;
      for (int i = 0; i < results.size(); i++)
        if (results.get(i).get()) {
          winner = i;
          count++;
        }
      assertEquals(1, count);
      r.repository().release("wrong-owner");
      assertFalse(r.repository().acquire("new"));
      r.repository().release("owner" + winner);
      assertTrue(r.repository().acquire("new"));
      r.repository().release("new");
    } finally {
      pool.shutdownNow();
    }
  }
}
