package edu.gla.cloudshield.core;

import java.nio.file.Path;
import java.util.Map;

public record Config(
    String mode,
    Path dataDir,
    String primaryRegion,
    String secondaryRegion,
    String primaryAccount,
    String secondaryAccount,
    String recoveryAccount,
    String sourceContainer,
    String primaryContainer,
    String secondaryContainer,
    String recoveryContainer,
    String table,
    String queue,
    String webhookUrl,
    int maxObjects,
    long maxBytes) {
  public static Config fromEnv() {
    return from(System.getenv());
  }

  public static Config from(Map<String, String> e) {
    String mode = e.getOrDefault("CLOUDSHIELD_MODE", "local");
    if (!mode.equals("local") && !mode.equals("azure"))
      throw new IllegalArgumentException("CLOUDSHIELD_MODE must be local or azure");
    Config c =
        new Config(
            mode,
            Path.of(e.getOrDefault("CLOUDSHIELD_DATA_DIR", "data")),
            e.getOrDefault("AZURE_PRIMARY_REGION", "centralindia"),
            e.getOrDefault("AZURE_SECONDARY_REGION", "koreacentral"),
            e.getOrDefault("AZURE_PRIMARY_ACCOUNT", ""),
            e.getOrDefault("AZURE_SECONDARY_ACCOUNT", ""),
            e.getOrDefault("AZURE_RECOVERY_ACCOUNT", ""),
            e.getOrDefault("SOURCE_CONTAINER", "source"),
            e.getOrDefault("PRIMARY_CONTAINER", "backups"),
            e.getOrDefault("SECONDARY_CONTAINER", "backups"),
            e.getOrDefault("RECOVERY_CONTAINER", "recovery"),
            e.getOrDefault("STATE_TABLE", "cloudshield"),
            e.getOrDefault("JOB_QUEUE", "cloudshield-jobs"),
            e.getOrDefault("ALERT_WEBHOOK_URL", ""),
            Integer.parseInt(e.getOrDefault("MAX_OBJECTS", "500")),
            Long.parseLong(e.getOrDefault("MAX_BYTES", "20971520")));
    if (c.maxObjects < 1 || c.maxObjects > 1000 || c.maxBytes < 1 || c.maxBytes > 104857600)
      throw new IllegalArgumentException("Invalid snapshot limits");
    if (c.azure()) {
      if (c.primaryRegion.equals(c.secondaryRegion))
        throw new IllegalArgumentException("Two distinct Azure regions are required");
      for (String account : new String[] {c.primaryAccount, c.secondaryAccount, c.recoveryAccount})
        if (!account.matches("[a-z0-9]{3,24}"))
          throw new IllegalArgumentException(
              "Three valid Azure storage account names are required");
      if (java.util.Set.of(c.primaryAccount, c.secondaryAccount, c.recoveryAccount).size() != 3)
        throw new IllegalArgumentException(
            "Primary, secondary and unversioned recovery accounts must differ");
      if (c.sourceContainer.equals(c.primaryContainer))
        throw new IllegalArgumentException("Source and backup containers must differ");
      for (String name :
          new String[] {
            c.sourceContainer,
            c.primaryContainer,
            c.secondaryContainer,
            c.recoveryContainer,
            c.queue
          })
        if (!name.matches("[a-z0-9](?:[a-z0-9]|-(?!-)){1,61}[a-z0-9]"))
          throw new IllegalArgumentException("Invalid Azure container or queue name");
      if (!c.table.matches("[A-Za-z][A-Za-z0-9]{2,62}"))
        throw new IllegalArgumentException("Invalid Azure table name");
    }
    if (!c.webhookUrl.isBlank() && !c.webhookUrl.startsWith("https://"))
      throw new IllegalArgumentException("Alert webhook must use HTTPS");
    return c;
  }

  public boolean azure() {
    return mode.equals("azure");
  }
}
