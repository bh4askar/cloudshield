package edu.gla.cloudshield.worker;

import com.amazonaws.services.lambda.runtime.*;
import edu.gla.cloudshield.core.*;
import edu.gla.cloudshield.core.Models.Job;
import java.util.*;

public class Handler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
  private static final RuntimeContext RUNTIME = RuntimeContext.create(Config.fromEnv());

  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    if (context.getRemainingTimeInMillis() < 60000)
      throw new IllegalStateException("Insufficient execution time");
    String tenantId = Objects.toString(event.get("tenantId"), "");
    if (tenantId.isBlank() && event.containsKey("scheduleId")) {
      var lambda =
          software.amazon.awssdk.services.lambda.LambdaClient.builder()
              .region(software.amazon.awssdk.regions.Region.of(RUNTIME.config().primaryRegion()))
              .build();
      try {
        List<String> ids = new ArrayList<>();
        ids.add("demo");
        RUNTIME.repository().accounts().forEach(account -> ids.add(account.id));
        for (String id : ids) {
          Map<String, Object> child = new HashMap<>(event);
          child.put("tenantId", id);
          lambda.invoke(
              r ->
                  r.functionName(context.getFunctionName())
                      .invocationType(
                          software.amazon.awssdk.services.lambda.model.InvocationType.EVENT)
                      .payload(
                          software.amazon.awssdk.core.SdkBytes.fromByteArray(Json.bytes(child))));
        }
        return Map.of("status", "DISPATCHED", "workspaces", ids.size());
      } finally {
        lambda.close();
      }
    }
    if (tenantId.isBlank()) tenantId = "demo";
    if (!tenantId.equals("demo") && RUNTIME.repository().account(tenantId).isEmpty())
      throw new IllegalArgumentException("Unknown workspace");
    RuntimeContext runtime = RUNTIME.forTenant(tenantId);
    String jobId = (String) event.get("jobId");
    if (jobId == null) {
      String type = Objects.toString(event.get("type"), "BACKUP");
      String scheduleId = Objects.toString(event.get("scheduleId"), "");
      // EventBridge Scheduler uses the scheduled time, stable across delivery retries.
      if (!scheduleId.isBlank()) {
        String stamp =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(java.time.Instant.parse(scheduleId));
        String stableId =
            stamp
                + "-"
                + BackupEngine.sha(
                        (type + "/" + scheduleId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .substring(0, 8);
        jobId = runtime.engine().createJob(type, null, "SCHEDULE", stableId).id;
      } else jobId = runtime.engine().createJob(type, (String) event.get("backupId"), "LAMBDA").id;
    }
    Job queued = runtime.repository().job(jobId).orElseThrow();
    queued.tenantId = tenantId;
    runtime.repository().save(queued);
    Job job = runtime.engine().execute(jobId);
    context.getLogger().log(new String(Json.bytes(job), java.nio.charset.StandardCharsets.UTF_8));
    return Map.of("jobId", job.id, "status", job.status, "message", job.message);
  }
}
