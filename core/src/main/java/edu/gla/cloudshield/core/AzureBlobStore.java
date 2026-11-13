package edu.gla.cloudshield.core;

import com.azure.core.util.Context;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import com.azure.storage.blob.options.*;
import java.time.Duration;
import java.util.*;

public final class AzureBlobStore implements BlobStore {
  private final BlobContainerClient container;
  private final long readLimit;

  public AzureBlobStore(BlobContainerClient container, long readLimit) {
    this.container = container;
    this.readLimit = readLimit;
  }

  public List<Blob> list(String prefix) {
    List<Blob> result = new ArrayList<>();
    for (BlobItem item :
        container.listBlobs(new ListBlobsOptions().setPrefix(prefix), Duration.ofSeconds(40))) {
      BlobProperties p = container.getBlobClient(item.getName()).getProperties();
      // Use immutable versions when available; otherwise fail a changed read using its ETag.
      String version = p.getVersionId() != null ? "v:" + p.getVersionId() : "e:" + p.getETag();
      result.add(new Blob(item.getName(), p.getBlobSize(), version));
      if (result.size() > 1000) throw new IllegalStateException("Object limit exceeded (1000)");
    }
    return result;
  }

  public byte[] read(String key, String version) {
    BlobClient blob = container.getBlobClient(key);
    BlobRequestConditions conditions = new BlobRequestConditions();
    if (version != null && version.startsWith("v:"))
      blob = blob.getVersionClient(version.substring(2));
    else if (version != null && version.startsWith("e:"))
      conditions.setIfMatch(version.substring(2));
    try (var input =
        blob.openInputStream(new BlobInputStreamOptions().setRequestConditions(conditions))) {
      byte[] data = input.readNBytes(Math.toIntExact(readLimit + 1));
      if (data.length > readLimit)
        throw new IllegalStateException("Object exceeds configured size limit");
      return data;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Azure blob read failed", e);
    }
  }

  public void write(String key, byte[] bytes) {
    if (bytes.length > readLimit)
      throw new IllegalArgumentException("Object exceeds configured size limit");
    container.getBlobClient(key).upload(com.azure.core.util.BinaryData.fromBytes(bytes), true);
  }

  public void deletePrefix(String prefix) {
    if (prefix == null || prefix.isBlank() || !prefix.endsWith("/"))
      throw new IllegalArgumentException("Explicit nonempty prefix required");
    for (Blob b : list(prefix))
      container
          .getBlobClient(b.key())
          .deleteIfExistsWithResponse(
              DeleteSnapshotsOptionType.INCLUDE, null, Duration.ofSeconds(40), Context.NONE);
    if (!list(prefix).isEmpty())
      throw new IllegalStateException("Recovery prefix still contains objects");
  }
}
