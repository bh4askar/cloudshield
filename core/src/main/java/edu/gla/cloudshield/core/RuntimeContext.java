package edu.gla.cloudshield.core;

import java.time.Duration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;

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
    SnsClient sns = null;
    if (c.aws()) {
      var overrides =
          ClientOverrideConfiguration.builder()
              .apiCallTimeout(Duration.ofSeconds(40))
              .apiCallAttemptTimeout(Duration.ofSeconds(15))
              .build();
      S3Client p =
          S3Client.builder()
              .region(Region.of(c.primaryRegion()))
              .httpClientBuilder(UrlConnectionHttpClient.builder())
              .overrideConfiguration(overrides)
              .build();
      S3Client s =
          S3Client.builder()
              .region(Region.of(c.secondaryRegion()))
              .httpClientBuilder(UrlConnectionHttpClient.builder())
              .overrideConfiguration(overrides)
              .build();
      source = new S3BlobStore(p, c.sourceBucket(), c.maxBytes());
      primary = new S3BlobStore(p, c.primaryBucket(), c.maxBytes());
      secondary = new S3BlobStore(s, c.secondaryBucket(), c.maxBytes());
      recovery = new S3BlobStore(p, c.recoveryBucket(), c.maxBytes());
      repository =
          new DynamoRepository(
              DynamoDbClient.builder()
                  .region(Region.of(c.primaryRegion()))
                  .httpClientBuilder(UrlConnectionHttpClient.builder())
                  .overrideConfiguration(overrides)
                  .build(),
              c.table());
      if (!c.topicArn().isBlank())
        sns =
            SnsClient.builder()
                .region(Region.of(c.primaryRegion()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(overrides)
                .build();
    } else {
      source = new LocalBlobStore(c.dataDir().resolve("source"));
      primary = new LocalBlobStore(c.dataDir().resolve("primary"));
      secondary = new LocalBlobStore(c.dataDir().resolve("secondary"));
      recovery = new LocalBlobStore(c.dataDir().resolve("recovery"));
      repository = new LocalRepository(c.dataDir().resolve("metadata"));
    }
    Alerts alerts = new Alerts(c, sns);
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
