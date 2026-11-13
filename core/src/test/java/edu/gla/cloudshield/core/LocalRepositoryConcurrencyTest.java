package edu.gla.cloudshield.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryConcurrencyTest {
  @TempDir Path temp;

  @Test
  void apiPollingAndWorkerUpdatesShareTenantLock() throws Exception {
    var root = new LocalRepository(temp);
    var writer = root.forTenant("a".repeat(32));
    var reader = root.forTenant("a".repeat(32));
    assertSame(writer, reader);
    var job = new Models.Job();
    job.id = "polling-race";
    job.status = "QUEUED";
    writer.save(job);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var updates = pool.submit(() -> {
        start.await();
        for (int i = 0; i < 200; i++) {
          job.status = "RUNNING";
          writer.save(job);
        }
        job.status = "SUCCEEDED";
        writer.save(job);
        return null;
      });
      var polling = pool.submit(() -> {
        start.await();
        for (int i = 0; i < 200; i++) {
          assertEquals(job.id, reader.job(job.id).orElseThrow().id);
          assertEquals(1, reader.jobs().size());
        }
        return null;
      });
      start.countDown();
      updates.get(20, TimeUnit.SECONDS);
      polling.get(20, TimeUnit.SECONDS);
    }
    assertEquals("SUCCEEDED", reader.job(job.id).orElseThrow().status);
  }
}
