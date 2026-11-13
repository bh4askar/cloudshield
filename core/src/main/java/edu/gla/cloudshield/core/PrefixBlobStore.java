package edu.gla.cloudshield.core;

import java.util.List;

/** Enforces a tenant boundary before forwarding any object-store operation. */
public final class PrefixBlobStore implements BlobStore {
  private final BlobStore delegate;
  private final String prefix;

  public PrefixBlobStore(BlobStore delegate, String tenantId) {
    if (!tenantId.matches("[a-f0-9]{32}"))
      throw new IllegalArgumentException("Invalid workspace ID");
    this.delegate = delegate;
    this.prefix = "tenants/" + tenantId + "/";
  }

  private String scoped(String key) {
    if (key == null
        || key.startsWith("/")
        || key.contains("\\")
        || key.contains(":")
        || java.util.Arrays.stream(key.split("/", -1))
            .anyMatch(p -> p.equals("..") || p.equals("."))) {
      throw new IllegalArgumentException("Unsafe workspace key");
    }
    return prefix + key;
  }

  public List<Blob> list(String key) {
    return delegate.list(scoped(key)).stream()
        .filter(b -> b.key().startsWith(prefix))
        .map(b -> new Blob(b.key().substring(prefix.length()), b.size(), b.version()))
        .toList();
  }

  public byte[] read(String key, String version) {
    return delegate.read(scoped(key), version);
  }

  public void write(String key, byte[] bytes) {
    delegate.write(scoped(key), bytes);
  }

  public void deletePrefix(String key) {
    if (key == null || key.isBlank() || !key.endsWith("/"))
      throw new IllegalArgumentException("Scoped prefix required");
    delegate.deletePrefix(scoped(key));
  }
}
