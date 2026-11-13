package edu.gla.cloudshield.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Local object-store emulator. Only used in explicit demo mode. */
public class LocalBlobStore implements BlobStore {
  private final Path root;

  public LocalBlobStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path resolve(String key) {
    if (key == null
        || key.isBlank()
        || key.indexOf('\\') >= 0
        || key.startsWith("/")
        || key.contains(":")) throw new IllegalArgumentException("Unsafe object key");
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root) || p.equals(root))
      throw new IllegalArgumentException("Unsafe object key");
    for (Path q = p; q != null && q.startsWith(root); q = q.getParent())
      if (Files.isSymbolicLink(q))
        throw new IllegalArgumentException("Symbolic links are not allowed");
    return p;
  }

  public synchronized List<Blob> list(String prefix) {
    try (var paths = Files.walk(root)) {
      return paths
          .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
          .map(
              p -> {
                String k = root.relativize(p).toString().replace('\\', '/');
                try {
                  return new Blob(k, Files.size(p), null);
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .filter(b -> b.key().startsWith(prefix))
          .sorted(java.util.Comparator.comparing(Blob::key))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot list local store", e);
    }
  }

  public synchronized byte[] read(String key, String version) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (IOException e) {
      throw new IllegalStateException("Object unavailable: " + key, e);
    }
  }

  public synchronized void write(String key, byte[] bytes) {
    Path p = resolve(key);
    try {
      Files.createDirectories(p.getParent());
      Path temp = Files.createTempFile(p.getParent(), ".cs-", ".tmp");
      try {
        Files.write(temp, bytes);
        Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot write object", e);
    }
  }

  public synchronized void deletePrefix(String prefix) {
    if (prefix == null || prefix.isBlank() || !prefix.endsWith("/"))
      throw new IllegalArgumentException("Explicit nonempty prefix required");
    for (Blob b : list(prefix))
      try {
        Files.deleteIfExists(resolve(b.key()));
      } catch (IOException e) {
        throw new IllegalStateException("Cleanup failed", e);
      }
    // Remove empty directories inside this prefix, leaving other restores intact.
    Path p = resolve(prefix);
    if (Files.exists(p))
      try (var paths = Files.walk(p)) {
        for (Path q : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(q);
      } catch (IOException e) {
        throw new IllegalStateException("Cleanup failed", e);
      }
  }
}
