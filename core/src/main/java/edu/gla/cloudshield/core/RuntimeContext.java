package edu.gla.cloudshield.core;

public record RuntimeContext(
    Config config,
    BlobStore source,
    BlobStore primary,
    BlobStore secondary,
    BlobStore recovery,
    Repository repository,
    BackupEngine engine,
    Alerts alerts) {
  public static RuntimeContext create(Config c) {
    BlobStore source, primary, secondary, recovery;
    Repository repository;
    if (c.azure()) {
      source =
          new AzureBlobStore(
              AzureClients.container(c.primaryAccount(), c.sourceContainer()), c.maxBytes());
      primary =
          new AzureBlobStore(
              AzureClients.container(c.primaryAccount(), c.primaryContainer()), c.maxBytes());
      secondary =
          new AzureBlobStore(
              AzureClients.container(c.secondaryAccount(), c.secondaryContainer()), c.maxBytes());
      recovery =
          new AzureBlobStore(
              AzureClients.container(c.recoveryAccount(), c.recoveryContainer()), c.maxBytes());
      repository = new AzureTableRepository(AzureClients.table(c));
    } else {
      source = new LocalBlobStore(c.dataDir().resolve("source"));
      primary = new LocalBlobStore(c.dataDir().resolve("primary"));
      secondary = new LocalBlobStore(c.dataDir().resolve("secondary"));
      recovery = new LocalBlobStore(c.dataDir().resolve("recovery"));
      repository = new LocalRepository(c.dataDir().resolve("metadata"));
    }
    Alerts alerts = new Alerts(c, null);
    return new RuntimeContext(
        c,
        source,
        primary,
        secondary,
        recovery,
        repository,
        new BackupEngine(c, source, primary, secondary, recovery, repository, alerts),
        alerts);
  }

  public RuntimeContext forTenant(String id) {
    if (id.equals("demo")) return this;
    BlobStore s = new PrefixBlobStore(source, id);
    BlobStore p = new PrefixBlobStore(primary, id);
    BlobStore b = new PrefixBlobStore(secondary, id);
    BlobStore r = new PrefixBlobStore(recovery, id);
    Repository repo = repository.forTenant(id);
    return new RuntimeContext(
        config, s, p, b, r, repo, new BackupEngine(config, s, p, b, r, repo, alerts), alerts);
  }
}
