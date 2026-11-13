package edu.gla.cloudshield.core;

import com.azure.core.util.Context;
import com.azure.data.tables.*;
import com.azure.data.tables.models.*;
import edu.gla.cloudshield.core.Models.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class AzureTableRepository implements Repository {
  private final TableClient client;
  private final String partition;

  public AzureTableRepository(TableClient client) {
    this(client, "CLOUDSHIELD");
  }

  private AzureTableRepository(TableClient client, String partition) {
    this.client = client;
    this.partition = partition;
  }

  // Azure Tables sorts ascending. Complement ASCII bytes so newest timestamp IDs sort first.
  static String row(String kind, String id) {
    if (!id.matches("[a-zA-Z0-9-]{1,100}"))
      throw new IllegalArgumentException("Invalid metadata ID");
    StringBuilder result = new StringBuilder(kind + "-");
    for (char c : id.toCharArray()) result.append(String.format("%02x", 127 - (int) c));
    return result.toString();
  }

  private void put(String kind, String id, Object value) {
    client.upsertEntityWithResponse(
        new TableEntity(partition, row(kind, id))
            .addProperty("payload", new String(Json.bytes(value), StandardCharsets.UTF_8)),
        TableEntityUpdateMode.REPLACE,
        Duration.ofSeconds(40),
        Context.NONE);
  }

  private <T> Optional<T> get(String kind, String id, Class<T> type) {
    try {
      return Optional.of(decode(client.getEntity(partition, row(kind, id)), type));
    } catch (TableServiceException e) {
      if (e.getResponse().getStatusCode() == 404) return Optional.empty();
      throw e;
    }
  }

  private <T> T decode(TableEntity entity, Class<T> type) {
    return Json.read(
        ((String) entity.getProperty("payload")).getBytes(StandardCharsets.UTF_8), type);
  }

  private <T> List<T> query(String kind, Class<T> type) {
    List<T> result = new ArrayList<>();
    var options =
        new ListEntitiesOptions()
            .setFilter(
                "PartitionKey eq '"
                    + partition
                    + "' and RowKey ge '"
                    + kind
                    + "-' and RowKey lt '"
                    + kind
                    + ".'")
            .setTop(500);
    for (TableEntity e : client.listEntities(options, Duration.ofSeconds(40), Context.NONE)) {
      result.add(decode(e, type));
      if (result.size() == 500) break;
    }
    return result;
  }

  public void save(Job j) {
    put("JOB", j.id, j);
  }

  public void save(Backup b) {
    put("BACKUP", b.id, b);
  }

  public void save(Account a) {
    put("ACCOUNT", a.id, a);
  }

  public Job create(Job job) {
    try {
      client.createEntity(
          new TableEntity(partition, row("JOB", job.id))
              .addProperty("payload", new String(Json.bytes(job), StandardCharsets.UTF_8)));
      return job;
    } catch (TableServiceException e) {
      if (e.getResponse().getStatusCode() == 409) return job(job.id).orElseThrow();
      throw e;
    }
  }

  public Optional<Job> job(String id) {
    return get("JOB", id, Job.class);
  }

  public Optional<Backup> backup(String id) {
    return get("BACKUP", id, Backup.class);
  }

  public Optional<Account> account(String id) {
    return get("ACCOUNT", id, Account.class);
  }

  public List<Job> jobs() {
    return query("JOB", Job.class);
  }

  public List<Backup> backups() {
    return query("BACKUP", Backup.class);
  }

  public List<Account> accounts() {
    return query("ACCOUNT", Account.class);
  }

  public boolean acquire(String owner) {
    long now = Instant.now().getEpochSecond();
    TableEntity lock =
        new TableEntity(partition, "LOCK")
            .addProperty("owner", owner)
            .addProperty("expiresAt", now + 960);
    try {
      client.createEntity(lock);
      return true;
    } catch (TableServiceException e) {
      if (e.getResponse().getStatusCode() != 409) throw e;
    }
    try {
      TableEntity current = client.getEntity(partition, "LOCK");
      if (((Number) current.getProperty("expiresAt")).longValue() >= now) return false;
      current.addProperty("owner", owner).addProperty("expiresAt", now + 960);
      client.updateEntityWithResponse(
          current, TableEntityUpdateMode.REPLACE, true, Duration.ofSeconds(40), Context.NONE);
      return true;
    } catch (TableServiceException e) {
      if (Set.of(404, 409, 412).contains(e.getResponse().getStatusCode())) return false;
      throw e;
    }
  }

  public void release(String owner) {
    try {
      TableEntity lock = client.getEntity(partition, "LOCK");
      if (owner.equals(lock.getProperty("owner")))
        client.deleteEntityWithResponse(lock, true, Duration.ofSeconds(40), Context.NONE);
    } catch (TableServiceException e) {
      if (!Set.of(404, 412).contains(e.getResponse().getStatusCode())) throw e;
    }
  }

  public Repository forTenant(String id) {
    if (!id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid workspace ID");
    return new AzureTableRepository(client, "TENANT-" + id);
  }
}
