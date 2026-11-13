package edu.gla.cloudshield.worker;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import edu.gla.cloudshield.core.*;
import edu.gla.cloudshield.core.Models.Job;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Functions {
  private static class Holder {
    static final RuntimeContext RUNTIME = RuntimeContext.create(Config.fromEnv());
  }

  @FunctionName("RunJob")
  public void run(
      @QueueTrigger(name = "message", queueName = "%JOB_QUEUE%", connection = "JobStorage")
          String message,
      ExecutionContext context) {
    Map<?, ?> event = Json.read(message.getBytes(StandardCharsets.UTF_8), Map.class);
    String tenant = Objects.toString(event.get("tenantId"), "demo");
    if (!tenant.equals("demo") && Holder.RUNTIME.repository().account(tenant).isEmpty())
      throw new IllegalArgumentException("Unknown workspace");
    RuntimeContext runtime = Holder.RUNTIME.forTenant(tenant);
    String id = Objects.toString(event.get("jobId"), "");
    if (id.isBlank()) {
      String type = Objects.toString(event.get("type"), "");
      id = scheduledId(type, Objects.toString(event.get("scheduled"), ""));
      runtime.engine().createJob(type, null, "SCHEDULE", id);
    }
    Job result = runtime.engine().execute(id);
    context
        .getLogger()
        .info("CloudShield result: " + new String(Json.bytes(result), StandardCharsets.UTF_8));
  }

  static String scheduledId(String type, String scheduled) {
    if (!Set.of("BACKUP", "TEST").contains(type))
      throw new IllegalArgumentException("Invalid schedule type");
    return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(scheduled))
        + "-"
        + BackupEngine.sha((type + "/" + scheduled).getBytes(StandardCharsets.UTF_8))
            .substring(0, 8);
  }

  @FunctionName("DailyBackup")
  public void daily(
      @TimerTrigger(name = "timer", schedule = "%BACKUP_SCHEDULE%") String timer,
      ExecutionContext context) {
    dispatch("BACKUP", timer, context);
  }

  @FunctionName("WeeklyRecoveryTest")
  public void weekly(
      @TimerTrigger(name = "timer", schedule = "%TEST_SCHEDULE%") String timer,
      ExecutionContext context) {
    dispatch("TEST", timer, context);
  }

  static String scheduledOccurrence(String timer) {
    // The host updates Last only AFTER invocation; Next is the occurrence being delivered.
    Map<?, ?> root = Json.read(timer.getBytes(StandardCharsets.UTF_8), Map.class);
    Object raw =
        root.containsKey("ScheduleStatus")
            ? root.get("ScheduleStatus")
            : root.get("scheduleStatus");
    if (!(raw instanceof Map<?, ?> status))
      throw new IllegalArgumentException("Timer schedule status missing");
    Object next = status.containsKey("Next") ? status.get("Next") : status.get("next");
    Instant occurrence = OffsetDateTime.parse(Objects.toString(next)).toInstant();
    if (occurrence.isBefore(Instant.parse("2020-01-01T00:00:00Z")))
      throw new IllegalArgumentException("Invalid scheduled occurrence");
    return occurrence.toString();
  }

  private void dispatch(String type, String timer, ExecutionContext context) {
    String scheduled = scheduledOccurrence(timer);
    List<String> ids = new ArrayList<>();
    ids.add("demo");
    Holder.RUNTIME.repository().accounts().forEach(a -> ids.add(a.id));
    var queue = AzureClients.queue(Holder.RUNTIME.config());
    for (String id : ids)
      queue.sendMessage(
          new String(
              Json.bytes(Map.of("tenantId", id, "type", type, "scheduled", scheduled)),
              StandardCharsets.UTF_8));
    context
        .getLogger()
        .info("Dispatched " + type + " for " + ids.size() + " workspaces at " + scheduled);
  }

  @FunctionName("PoisonJob")
  public void poison(
      @QueueTrigger(name = "message", queueName = "%JOB_QUEUE%-poison", connection = "JobStorage")
          String message,
      ExecutionContext context) {
    Map<?, ?> event = Json.read(message.getBytes(StandardCharsets.UTF_8), Map.class);
    String tenant = Objects.toString(event.get("tenantId"), "demo");
    RuntimeContext runtime = Holder.RUNTIME.forTenant(tenant);
    String id = Objects.toString(event.get("jobId"), "");
    if (id.isBlank())
      id =
          scheduledId(
              Objects.toString(event.get("type")), Objects.toString(event.get("scheduled")));
    runtime
        .repository()
        .job(id)
        .ifPresent(
            job -> {
              if (Set.of("QUEUED", "RUNNING").contains(job.status)) {
                job.status = "FAILED";
                job.finishedAt = Instant.now().toString();
                job.message =
                    "Azure queue exhausted worker retries; inspect Function logs and submit again.";
                runtime.alerts().send(job);
                runtime.repository().save(job);
              }
            });
    context.getLogger().severe("CloudShield poison job " + id + " requires investigation");
  }
}
