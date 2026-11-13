package edu.gla.cloudshield.core;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public final class S3BlobStore implements BlobStore {
  private final S3Client client;
  private final String bucket;
  private final long readLimit;

  public S3BlobStore(S3Client client, String bucket, long readLimit) {
    this.client = client;
    this.bucket = bucket;
    this.readLimit = readLimit;
  }

  public List<Blob> list(String prefix) {
    List<Blob> result = new ArrayList<>();
    for (ListObjectsV2Response page :
        client.listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()))
      for (S3Object o : page.contents()) {
        HeadObjectResponse h = client.headObject(b -> b.bucket(bucket).key(o.key()));
        result.add(new Blob(o.key(), h.contentLength(), h.versionId()));
        if (result.size() > 1000)
          throw new IllegalStateException("Object limit exceeded (1000); reduce the dataset");
      }
    return result;
  }

  public byte[] read(String key, String version) {
    var request = GetObjectRequest.builder().bucket(bucket).key(key).versionId(version).build();
    try (var input = client.getObject(request)) {
      if (input.response().contentLength() > readLimit)
        throw new IllegalStateException("Object exceeds configured size limit");
      byte[] bytes = input.readNBytes(Math.toIntExact(readLimit + 1));
      if (bytes.length > readLimit)
        throw new IllegalStateException("Object exceeds configured size limit");
      return bytes;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("S3 object read failed", e);
    }
  }

  public void write(String key, byte[] bytes) {
    client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .tagging(
                key.startsWith("tests/") || key.matches("tenants/[a-f0-9]{32}/tests/.*")
                    ? "cloudshield-temporary=true"
                    : null)
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build(),
        RequestBody.fromBytes(bytes));
  }

  public void deletePrefix(String prefix) {
    if (prefix == null || prefix.isBlank() || !prefix.endsWith("/"))
      throw new IllegalArgumentException("Explicit nonempty prefix required");
    // Recovery bucket is deliberately unversioned: deletion removes bytes, not just a delete
    // marker.
    for (Blob b : list(prefix)) client.deleteObject(r -> r.bucket(bucket).key(b.key()));
    if (!list(prefix).isEmpty())
      throw new IllegalStateException("Recovery prefix still contains objects");
  }
}
