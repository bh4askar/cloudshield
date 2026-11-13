package edu.gla.cloudshield.core;

import java.nio.file.Path;
import java.util.Map;

public record Config(
    String mode,
    Path dataDir,
    String primaryRegion,
    String secondaryRegion,
    String sourceBucket,
    String primaryBucket,
    String secondaryBucket,
    String recoveryBucket,
    String table,
    String topicArn,
    String webhookUrl,
    String workerFunction,
    int maxObjects,
    long maxBytes) {
  public static Config fromEnv() {
    return from(System.getenv());
  }

  public static Config from(Map<String, String> e) {
    String mode = e.getOrDefault("CLOUDSHIELD_MODE", "local");
    if (!mode.equals("local") && !mode.equals("aws"))
      throw new IllegalArgumentException("CLOUDSHIELD_MODE must be local or aws");
    Config c =
        new Config(
            mode,
            Path.of(e.getOrDefault("CLOUDSHIELD_DATA_DIR", "data")),
            e.getOrDefault("AWS_REGION", "ap-south-1"),
            e.getOrDefault("SECONDARY_REGION", "ap-southeast-1"),
            e.getOrDefault("SOURCE_BUCKET", ""),
            e.getOrDefault("PRIMARY_BUCKET", ""),
            e.getOrDefault("SECONDARY_BUCKET", ""),
            e.getOrDefault("RECOVERY_BUCKET", ""),
            e.getOrDefault("STATE_TABLE", ""),
            e.getOrDefault("SNS_TOPIC_ARN", ""),
            e.getOrDefault("ALERT_WEBHOOK_URL", ""),
            e.getOrDefault("WORKER_FUNCTION", ""),
            Integer.parseInt(e.getOrDefault("MAX_OBJECTS", "500")),
            Long.parseLong(e.getOrDefault("MAX_BYTES", "20971520")));
    if (c.maxObjects < 1 || c.maxObjects > 1000 || c.maxBytes < 1 || c.maxBytes > 104857600)
      throw new IllegalArgumentException("Invalid snapshot limits");
    if (c.aws()) {
      if (c.primaryRegion.equals(c.secondaryRegion))
        throw new IllegalArgumentException("Two distinct AWS regions are required");
      if (c.sourceBucket.isBlank()
          || c.primaryBucket.isBlank()
          || c.secondaryBucket.isBlank()
          || c.recoveryBucket.isBlank()
          || c.table.isBlank())
        throw new IllegalArgumentException("AWS bucket names and STATE_TABLE are required");
      if (java.util.Set.of(c.sourceBucket, c.primaryBucket, c.secondaryBucket, c.recoveryBucket)
              .size()
          != 4)
        throw new IllegalArgumentException("Source, backup and recovery buckets must differ");
    }
    if (!c.webhookUrl.isBlank() && !c.webhookUrl.startsWith("https://"))
      throw new IllegalArgumentException("Alert webhook must use HTTPS");
    return c;
  }

  public boolean aws() {
    return mode.equals("aws");
  }
}
