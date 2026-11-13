package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.*;
import edu.gla.cloudshield.core.Models.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class ApiController {
  private final WorkspaceService workspaces;
  private final JobDispatcher dispatcher;

  public ApiController(WorkspaceService workspaces, JobDispatcher dispatcher) {
    this.workspaces = workspaces;
    this.dispatcher = dispatcher;
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/public/mode")
  Map<String, Boolean> publicMode() {
    return Map.of("demo", !Config.fromEnv().azure(), "googleEnabled", GoogleIdentity.enabled());
  }

  @GetMapping("/api/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "token",
        token.getToken(),
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName());
  }

  @GetMapping("/api/overview")
  Map<String, Object> overview(Authentication authentication) {
    var workspace = workspaces.current(authentication);
    RuntimeContext runtime = workspace.runtime();
    var backups = runtime.repository().backups();
    var jobs = runtime.repository().jobs();
    var tests = jobs.stream().filter(j -> j.type.equals("TEST")).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", runtime.config().mode());
    result.put(
        "account",
        Map.of("id", workspace.id(), "name", workspace.name(), "email", workspace.email()));
    result.put("primaryRegion", runtime.config().primaryRegion());
    result.put("secondaryRegion", runtime.config().secondaryRegion());
    result.put("backups", backups);
    result.put("jobs", jobs);
    result.put("backupCount", backups.size());
    result.put(
        "replicatedCount", backups.stream().filter(b -> "REPLICATED".equals(b.status)).count());
    result.put("protectedBytes", backups.stream().mapToLong(b -> b.bytes).sum());
    result.put("lastTest", tests.isEmpty() ? null : tests.getFirst());
    result.put(
        "pendingJobs",
        jobs.stream().filter(j -> Set.of("RUNNING", "QUEUED").contains(j.status)).count());
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    ZonedDateTime daily = now.withHour(2).withMinute(0).withSecond(0).withNano(0);
    if (!daily.isAfter(now)) daily = daily.plusDays(1);
    ZonedDateTime weekly =
        now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(2)
            .withMinute(30)
            .withSecond(0)
            .withNano(0);
    if (!weekly.isAfter(now)) weekly = weekly.plusWeeks(1);
    result.put("nextBackup", daily.toInstant().toString());
    result.put("nextTest", weekly.toInstant().toString());
    result.put("scheduleTimezone", "Asia/Kolkata");
    result.put(
        "notifications",
        runtime.config().webhookUrl().isBlank()
            ? (runtime.config().azure()
                ? "Azure worker logs and dashboard activity"
                : "Local event log")
            : "Configured external alerts");
    result.put("maxObjects", runtime.config().maxObjects());
    result.put("maxBytes", runtime.config().maxBytes());
    return result;
  }

  public record JobRequest(String type, String backupId) {}

  @PostMapping("/api/jobs")
  ResponseEntity<Job> create(@RequestBody JobRequest request, Authentication authentication) {
    var workspace = workspaces.current(authentication);
    if (request.type() == null || !Set.of("BACKUP", "TEST", "RESTORE").contains(request.type()))
      throw new IllegalArgumentException("Unknown operation");
    return ResponseEntity.accepted()
        .body(
            dispatcher.submit(
                workspace.runtime(),
                workspace.id(),
                request.type(),
                request.backupId(),
                "DASHBOARD"));
  }

  @GetMapping("/api/jobs/{id}")
  Job job(@PathVariable("id") String id, Authentication authentication) {
    RuntimeContext runtime = workspaces.current(authentication).runtime();
    return runtime
        .repository()
        .job(id)
        .orElseThrow(() -> new IllegalArgumentException("Job not found"));
  }

  @GetMapping("/api/sample")
  Object sample(
      @RequestParam(name = "restoreJobId", required = false) String restoreJobId,
      Authentication authentication) {
    RuntimeContext runtime = workspaces.current(authentication).runtime();
    if (restoreJobId == null)
      return Json.read(runtime.source().read("application/registrations.json"), Object.class);
    Job job =
        runtime
            .repository()
            .job(restoreJobId)
            .orElseThrow(() -> new IllegalArgumentException("Restore job not found"));
    if (!job.type.equals("RESTORE") || !job.status.equals("SUCCEEDED"))
      throw new IllegalArgumentException("A successful restore is required");
    return Json.read(
        runtime.recovery().read(job.restorePrefix + "application/registrations.json"),
        Object.class);
  }

  @PostMapping("/api/sample/change")
  Map<String, String> change(Authentication authentication) {
    var workspace = workspaces.current(authentication);
    dispatcher.submit(workspace.runtime(), workspace.id(), "CHANGE", null, "DASHBOARD");
    return Map.of(
        "message", "Registration update queued. Your application will refresh when it finishes.");
  }

  @GetMapping(value = "/api/report", produces = "application/json")
  ResponseEntity<byte[]> report(Authentication authentication) {
    RuntimeContext runtime = workspaces.current(authentication).runtime();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cloudshield-report.json")
        .body(
            Json.bytes(
                Map.of(
                    "generatedAt",
                    Instant.now().toString(),
                    "mode",
                    runtime.config().mode(),
                    "jobs",
                    runtime.repository().jobs(),
                    "backups",
                    runtime.repository().backups())));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", Objects.requireNonNullElse(e.getMessage(), "Invalid request")));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> failure(Exception e) {
    System.err.println("API error: " + e);
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "message",
                "Operation unavailable. Check the application log and storage configuration."));
  }
}
