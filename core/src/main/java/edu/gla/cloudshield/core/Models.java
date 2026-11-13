package edu.gla.cloudshield.core;

import java.util.ArrayList;
import java.util.List;

public final class Models {
  private Models() {}

  public record Entry(String key, long bytes, String sha256) {}

  public static class Manifest {
    public int schemaVersion = 1;
    public String id, createdAt;
    public List<Entry> entries = new ArrayList<>();
  }

  public static class Backup {
    public String id, createdAt, completedAt, status, manifestSha256;
    public int objectCount;
    public long bytes;
  }

  public static class Account {
    public String id, name, email, createdAt;
  }

  public static class Job {
    public String tenantId;
    public String id, type, trigger, status, createdAt, startedAt, finishedAt, backupId, message;
    public String regionUsed,
        cleanupStatus = "NOT_APPLICABLE",
        notificationStatus = "PENDING",
        notificationError;
    public Long rtoMillis, rpoSeconds, changedBytes;
    public Integer changedObjects, deletedObjects, verifiedObjects;
    public String restorePrefix;
  }
}
