package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.*;
import edu.gla.cloudshield.core.Models.Job;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;

@Service
public class JobDispatcher {
  private final RuntimeContext runtime;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final LambdaClient lambda;

  public JobDispatcher(RuntimeContext runtime) {
    this.runtime = runtime;
    lambda =
        runtime.config().aws()
            ? LambdaClient.builder().region(Region.of(runtime.config().primaryRegion())).build()
            : null;
    // In-process demo jobs cannot survive a stopped JVM. Record interruption visibly.
    if (!runtime.config().aws())
      for (Job job : runtime.repository().jobs())
        if (job.status.equals("QUEUED") || job.status.equals("RUNNING")) {
          job.status = "FAILED";
          job.finishedAt = Instant.now().toString();
          job.message = "Demo worker stopped before completion; retry the operation";
          if ("TEST".equals(job.type) && job.restorePrefix != null)
            try {
              runtime.recovery().deletePrefix(job.restorePrefix);
              job.cleanupStatus = "CLEANED";
            } catch (Exception e) {
              job.cleanupStatus = "FAILED";
            }
          runtime.repository().save(job);
        }
  }

  public Job submit(String type, String backupId, String trigger) {
    return submit(runtime, "demo", type, backupId, trigger);
  }

  public synchronized Job submit(
      RuntimeContext runtime, String tenantId, String type, String backupId, String trigger) {
    if (!tenantId.equals("demo") && !tenantId.matches("[a-f0-9]{32}"))
      throw new IllegalArgumentException("Invalid workspace");
    if (!tenantId.equals("demo") && trigger.equals("DASHBOARD")) {
      Instant since = Instant.now().minusSeconds(3600);
      long recent =
          runtime.repository().jobs().stream()
              .filter(
                  j ->
                      j.createdAt != null
                          && Instant.parse(j.createdAt).isAfter(since)
                          && "DASHBOARD".equals(j.trigger))
              .count();
      if (recent >= 30)
        throw new IllegalArgumentException(
            "Workspace limit reached: 30 manual operations per hour. Please try later.");
    }
    long pending =
        runtime.repository().jobs().stream()
            .filter(j -> j.status.equals("QUEUED") || j.status.equals("RUNNING"))
            .count();
    if (pending >= 5)
      throw new IllegalArgumentException("Five jobs are already queued. Wait for them to finish.");
    Job job = runtime.engine().createJob(type, backupId, trigger);
    job.tenantId = tenantId;
    runtime.repository().save(job);
    try {
      if (runtime.config().aws()) {
        if (runtime.config().workerFunction().isBlank())
          throw new IllegalStateException("WORKER_FUNCTION is not configured");
        lambda.invoke(
            r ->
                r.functionName(runtime.config().workerFunction())
                    .invocationType(InvocationType.EVENT)
                    .payload(
                        SdkBytes.fromByteArray(
                            Json.bytes(Map.of("jobId", job.id, "tenantId", tenantId)))));
      } else worker.execute(() -> {
        try { runtime.engine().execute(job.id); }
        catch (RuntimeException | LinkageError failure) {
          failure.printStackTrace();
          Job failed=runtime.repository().job(job.id).orElse(job);
          failed.status="FAILED";failed.finishedAt=Instant.now().toString();
          failed.message="Worker execution failed: "+failure.getClass().getSimpleName()+". Check the server log.";
          runtime.repository().save(failed);runtime.alerts().send(failed);runtime.repository().save(failed);
        }
      });
    } catch (Exception e) {
      job.status = "FAILED";
      job.finishedAt = Instant.now().toString();
      job.message = "Could not dispatch job; verify the worker configuration";
      runtime.repository().save(job);
      throw new IllegalStateException(job.message, e);
    }
    return job;
  }

  @PreDestroy
  void close() {
    worker.shutdown();
    if (lambda != null) lambda.close();
  }
}
