package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared engine for the web demo and the serverless AWS worker. */
public final class BackupEngine {
  private final Config config;
  private final BlobStore source, primary, secondary, recovery;
  private final Repository repository;
  private final Alerts alerts;

  public BackupEngine(
      Config config,
      BlobStore source,
      BlobStore primary,
      BlobStore secondary,
      BlobStore recovery,
      Repository repository,
      Alerts alerts) {
    this.config = config;
    this.source = source;
    this.primary = primary;
    this.secondary = secondary;
    this.recovery = recovery;
    this.repository = repository;
    this.alerts = alerts;
  }

  public static String id() {
    return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        + "-"
        + UUID.randomUUID().toString().substring(0, 8);
  }

  public static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Job createJob(String type, String backupId, String trigger) {
    return createJob(type, backupId, trigger, id());
  }

  public Job createJob(String type, String backupId, String trigger, String jobId) {
    if (type == null || !Set.of("BACKUP", "TEST", "RESTORE", "SETUP", "CHANGE").contains(type))
      throw new IllegalArgumentException("Unknown job type");
    if (jobId == null || !jobId.matches("[a-zA-Z0-9-]{1,100}"))
      throw new IllegalArgumentException("Invalid job ID");
    var existing = repository.job(jobId);
    if (existing.isPresent()) return existing.get();
    if (backupId != null && !backupId.matches("[a-zA-Z0-9-]{1,100}"))
      throw new IllegalArgumentException("Invalid backup ID");
    if (type.equals("RESTORE") && backupId == null)
      throw new IllegalArgumentException("Select a backup to restore");
    if (backupId != null && repository.backup(backupId).isEmpty())
      throw new IllegalArgumentException("Backup does not exist");
    Job job = new Job();
    job.id = jobId;
    job.type = type;
    job.backupId = backupId;
    job.trigger = trigger;
    job.status = "QUEUED";
    job.createdAt = Instant.now().toString();
    job.message = "Waiting for worker";
    repository.save(job);
    return job;
  }

  public Job execute(String jobId) {
    if (!repository.acquire(jobId))
      throw new IllegalStateException("Worker lease is busy; retry after the running job finishes");
    try {
      recoverInterruptedJobs(jobId);
      Job job =
          repository.job(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
      if (Set.of("SUCCEEDED", "FAILED").contains(job.status))
        return job; // Idempotent Lambda redelivery.
      job.status = "RUNNING";
      job.startedAt = Instant.now().toString();
      job.message = "Operation in progress";
      repository.save(job);
      try {
        if (job.type.equals("SETUP")) {
          SampleData.seed(source);
          backup(job);
        } else if (job.type.equals("CHANGE")) {
          SampleData.addRegistration(source);
          job.message = "A registration was added to your cloud application";
        } else if (job.type.equals("BACKUP")) backup(job);
        else restore(job, job.type.equals("TEST"));
        job.status = "SUCCEEDED";
      } catch (Exception e) {
        job.status = "FAILED";
        job.message = safeError(e);
      } finally {
        job.finishedAt = Instant.now().toString();
        repository.save(job);
        alerts.send(job);
        repository.save(job);
      }
      return job;
    } finally {
      repository.release(jobId);
    }
  }

  private void recoverInterruptedJobs(String currentId) {
    Instant cutoff = Instant.now().minusSeconds(960);
    for (Job old : repository.jobs())
      if (!old.id.equals(currentId)
          && "RUNNING".equals(old.status)
          && old.startedAt != null
          && Instant.parse(old.startedAt).isBefore(cutoff)) {
        old.status = "FAILED";
        old.finishedAt = Instant.now().toString();
        old.message =
            "Previous worker execution stopped before completion. Submit a new operation.";
        if ("TEST".equals(old.type) && old.restorePrefix != null)
          try {
            recovery.deletePrefix(old.restorePrefix);
            old.cleanupStatus = "CLEANED";
          } catch (Exception e) {
            old.cleanupStatus = "FAILED";
            old.message += " Cleanup retry failed.";
          }
        repository.save(old);
        alerts.send(old);
        repository.save(old);
      }
  }

  private static String safeError(Exception e) {
    String s = e.getMessage();
    return s == null ? e.getClass().getSimpleName() : s.substring(0, Math.min(s.length(), 400));
  }

  private Map<String, byte[]> capture() {
    List<BlobStore.Blob> objects = source.list("application/");
    if (objects.isEmpty())
      throw new IllegalStateException(
          "No source objects under application/. Seed the sample cloud application first.");
    if (objects.size() > config.maxObjects())
      throw new IllegalStateException("Snapshot object limit exceeded");
    if (objects.stream().mapToLong(BlobStore.Blob::size).sum() > config.maxBytes())
      throw new IllegalStateException("Snapshot byte limit exceeded");
    Map<String, byte[]> result = new TreeMap<>();
    long size = 0;
    for (var object : objects) {
      validKey(object.key());
      byte[] bytes = source.read(object.key(), object.version());
      size += bytes.length;
      if (size > config.maxBytes())
        throw new IllegalStateException("Snapshot grew beyond byte limit");
      result.put(object.key(), bytes);
    }
    return result;
  }

  private void backup(Job job) {
    int limit = Integer.parseInt(System.getenv().getOrDefault("MAX_BACKUPS_PER_ACCOUNT", "30"));
    if (repository.backups().size() >= limit)
      throw new IllegalStateException(
          "Workspace backup limit reached. Ask the operator to review retention before adding more"
              + " snapshots.");
    String backupId = job.id;
    job.backupId = backupId;
    repository.save(job);
    Manifest manifest = new Manifest();
    manifest.id = backupId;
    manifest.createdAt = Instant.now().toString();
    Map<String, byte[]> captured = capture();
    captured.forEach((k, v) -> manifest.entries.add(new Entry(k, v.length, sha(v))));
    byte[] document = Json.bytes(manifest);
    String prefix = "backups/" + backupId + "/";
    Backup backup = new Backup();
    backup.id = backupId;
    backup.createdAt = manifest.createdAt;
    backup.manifestSha256 = sha(document);
    backup.objectCount = captured.size();
    backup.bytes = captured.values().stream().mapToLong(b -> b.length).sum();
    writeAndVerify(primary, prefix, captured);
    primary.write(prefix + "manifest.json", document);
    if (!sha(primary.read(prefix + "manifest.json")).equals(backup.manifestSha256))
      throw new IllegalStateException("Primary manifest verification failed");
    backup.status = "PRIMARY_ONLY";
    repository.save(backup);
    try {
      writeAndVerify(secondary, prefix, captured);
      secondary.write(prefix + "manifest.json", document);
      if (!sha(secondary.read(prefix + "manifest.json")).equals(backup.manifestSha256))
        throw new IllegalStateException("Secondary manifest verification failed");
      backup.status = "REPLICATED";
      backup.completedAt = Instant.now().toString();
      repository.save(backup);
      job.message = "Snapshot verified in both regions";
      job.verifiedObjects = captured.size();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Primary backup is available, but secondary copy failed. Create another backup after"
              + " fixing the secondary region. "
              + safeError(e),
          e);
    }
  }

  private void writeAndVerify(BlobStore destination, String prefix, Map<String, byte[]> captured) {
    for (var entry : captured.entrySet()) {
      String key = prefix + "files/" + entry.getKey();
      destination.write(key, entry.getValue());
      if (!sha(entry.getValue()).equals(sha(destination.read(key))))
        throw new IllegalStateException("Stored checksum mismatch: " + entry.getKey());
    }
  }

  private record Snapshot(Manifest manifest, Map<String, byte[]> data, String region) {}

  private Snapshot load(Backup backup, BlobStore store, String region) {
    String prefix = "backups/" + backup.id + "/";
    byte[] document = store.read(prefix + "manifest.json");
    if (!sha(document).equals(backup.manifestSha256))
      throw new IllegalStateException("Manifest checksum mismatch");
    Manifest manifest = Json.read(document, Manifest.class);
    if (manifest.schemaVersion != 1
        || !backup.id.equals(manifest.id)
        || manifest.entries == null
        || manifest.entries.isEmpty()
        || manifest.entries.size() > config.maxObjects())
      throw new IllegalStateException("Invalid backup manifest");
    Map<String, byte[]> result = new TreeMap<>();
    long bytes = 0;
    for (Entry e : manifest.entries) {
      validKey(e.key());
      if (result.containsKey(e.key())) throw new IllegalStateException("Duplicate manifest key");
      if (e.bytes() < 0 || e.bytes() > config.maxBytes() || bytes + e.bytes() > config.maxBytes())
        throw new IllegalStateException("Manifest byte limit exceeded");
      byte[] value = store.read(prefix + "files/" + e.key());
      bytes += value.length;
      if (bytes > config.maxBytes() || value.length != e.bytes() || !sha(value).equals(e.sha256()))
        throw new IllegalStateException("Backup object checksum mismatch: " + e.key());
      result.put(e.key(), value);
    }
    return new Snapshot(manifest, result, region);
  }

  private Snapshot available(Backup backup) {
    try {
      return load(backup, primary, config.primaryRegion());
    } catch (Exception primaryError) {
      try {
        return load(backup, secondary, config.secondaryRegion());
      } catch (Exception secondaryError) {
        throw new IllegalStateException(
            "Neither backup copy passed verification. Primary: "
                + safeError(primaryError)
                + "; secondary: "
                + safeError(secondaryError));
      }
    }
  }

  private void restore(Job job, boolean test) {
    if (job.backupId == null)
      job.backupId =
          repository.backups().stream()
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("Create a backup before running a restore test"))
              .id;
    Backup backup =
        repository
            .backup(job.backupId)
            .orElseThrow(() -> new IllegalArgumentException("Backup not found"));
    String prefix = (test ? "tests/" : "restores/") + job.id + "/";
    job.restorePrefix = prefix;
    job.cleanupStatus = test ? "PENDING" : "RETAINED";
    repository.save(job);
    long start = System.nanoTime();
    try {
      Snapshot snapshot = available(backup);
      job.regionUsed = snapshot.region;
      job.rpoSeconds =
          Math.max(
              0,
              Duration.between(
                      Instant.parse(snapshot.manifest.createdAt), Instant.parse(job.startedAt))
                  .getSeconds());
      for (var entry : snapshot.data.entrySet())
        recovery.write(prefix + entry.getKey(), entry.getValue());
      for (var entry : snapshot.data.entrySet()) {
        byte[] restored = recovery.read(prefix + entry.getKey());
        if (!sha(restored).equals(sha(entry.getValue())))
          throw new IllegalStateException("Restored checksum mismatch: " + entry.getKey());
        if (entry.getKey().endsWith(".json"))
          try {
            Json.MAPPER.readTree(restored);
          } catch (Exception e) {
            throw new IllegalStateException(
                "Restored application JSON is invalid: " + entry.getKey());
          }
      }
      sampleHealthCheck(snapshot.data);
      job.verifiedObjects = snapshot.data.size();
      job.rtoMillis = Math.max(1, (System.nanoTime() - start) / 1_000_000);
      job.message =
          test
              ? "Restore, checksum and sample application health checks passed"
              : "Verified recovery copy is ready in " + prefix;
      try {
        measureDelta(job, snapshot.data);
      } catch (Exception e) {
        job.message +=
            ". Live source comparison unavailable; changed-data metrics were not measured.";
      }
    } finally {
      if (test) {
        try {
          recovery.deletePrefix(prefix);
          if (!recovery.list(prefix).isEmpty())
            throw new IllegalStateException("Temporary objects remain");
          job.cleanupStatus = "CLEANED";
        } catch (Exception cleanupError) {
          job.cleanupStatus = "FAILED";
          throw new IllegalStateException(
              "Temporary environment cleanup failed: " + safeError(cleanupError), cleanupError);
        }
      }
    }
  }

  private void measureDelta(Job job, Map<String, byte[]> restored) {
    Map<String, byte[]> current = capture();
    int changed = 0;
    long bytes = 0;
    for (var e : current.entrySet())
      if (!restored.containsKey(e.getKey())
          || !sha(restored.get(e.getKey())).equals(sha(e.getValue()))) {
        changed++;
        bytes += e.getValue().length;
      }
    job.changedObjects = changed;
    job.changedBytes = bytes;
    job.deletedObjects =
        (int) restored.keySet().stream().filter(k -> !current.containsKey(k)).count();
  }

  private static void sampleHealthCheck(Map<String, byte[]> data) {
    if (data.containsKey("application/registrations.json"))
      try {
        var root = Json.MAPPER.readTree(data.get("application/registrations.json"));
        if (!root.path("registrations").isArray() || root.path("schemaVersion").asInt() != 1)
          throw new IllegalStateException("Sample application schema health check failed");
        Set<String> ids = new HashSet<>();
        for (var row : root.path("registrations")) {
          String id = row.path("id").asText();
          if (id.isBlank() || row.path("student").asText().isBlank() || !ids.add(id))
            throw new IllegalStateException("Sample registration integrity check failed");
        }
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Sample application health check failed", e);
      }
  }

  private static void validKey(String key) {
    if (key == null
        || !key.startsWith("application/")
        || key.contains("\\")
        || key.contains(":")
        || Arrays.asList(key.split("/", -1)).stream()
            .anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
      throw new IllegalArgumentException("Unsafe or out-of-scope object key");
  }
}
