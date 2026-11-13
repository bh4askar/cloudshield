package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

public final class LocalRepository implements Repository {
  private final Path root;
  private final java.util.concurrent.ConcurrentHashMap<String, Repository> tenants =
      new java.util.concurrent.ConcurrentHashMap<>();
  private FileChannel channel;
  private FileLock lock;

  public LocalRepository(Path root) {
    this.root = root;
    try {
      Files.createDirectories(root.resolve("accounts"));
      Files.createDirectories(root.resolve("jobs"));
      Files.createDirectories(root.resolve("backups"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path file(String folder, String id) {
    if (id == null || !id.matches("[a-zA-Z0-9-]{1,100}"))
      throw new IllegalArgumentException("Invalid ID");
    return root.resolve(folder).resolve(id + ".json");
  }

  private synchronized void put(String folder, String id, Object value) {
    Path dest = file(folder, id);
    try {
      Path t = Files.createTempFile(dest.getParent(), "state-", ".tmp");
      try {
        Files.write(t, Json.bytes(value));
        Files.move(t, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(t);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot persist metadata", e);
    }
  }

  private synchronized <T> Optional<T> get(String folder, String id, Class<T> type) {
    Path p = file(folder, id);
    if (!Files.exists(p)) return Optional.empty();
    try {
      return Optional.of(Json.read(Files.readAllBytes(p), type));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private synchronized <T> List<T> all(String folder, Class<T> type) {
    try (var files = Files.list(root.resolve(folder))) {
      return files
          .filter(p -> p.toString().endsWith(".json"))
          .sorted(Comparator.reverseOrder())
          .map(
              p -> {
                try {
                  return Json.read(Files.readAllBytes(p), type);
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .limit(500)
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public void save(Job j) {
    put("jobs", j.id, j);
  }

  public void save(Backup b) {
    put("backups", b.id, b);
  }

  public Optional<Job> job(String id) {
    return get("jobs", id, Job.class);
  }

  public Optional<Backup> backup(String id) {
    return get("backups", id, Backup.class);
  }

  public List<Job> jobs() {
    return all("jobs", Job.class);
  }

  public List<Backup> backups() {
    return all("backups", Backup.class);
  }

  public synchronized boolean acquire(String owner) {
    try {
      if (lock != null) return false;
      channel =
          FileChannel.open(
              root.resolve("worker.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      try {
        lock = channel.tryLock();
      } catch (OverlappingFileLockException e) {
        channel.close();
        return false;
      }
      if (lock == null) channel.close();
      return lock != null;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized void release(String owner) {
    try {
      if (lock != null) {
        lock.release();
        channel.close();
        lock = null;
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public void save(Account account) {
    put("accounts", account.id, account);
  }

  public Optional<Account> account(String id) {
    return get("accounts", id, Account.class);
  }

  public List<Account> accounts() {
    return all("accounts", Account.class);
  }

  public Repository forTenant(String id) {
    if (!id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid workspace ID");
    return tenants.computeIfAbsent(
        id, key -> new LocalRepository(root.resolve("tenants").resolve(key)));
  }
}
