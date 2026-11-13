package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.*;
import java.time.Instant;
import java.util.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public final class DynamoRepository implements Repository {
  private final DynamoDbClient client;
  private final String table;
  private final String partition;

  public DynamoRepository(DynamoDbClient client, String table) {
    this(client, table, "CLOUDSHIELD");
  }

  private DynamoRepository(DynamoDbClient client, String table, String partition) {
    this.partition = partition;
    this.client = client;
    this.table = table;
  }

  private static AttributeValue s(String v) {
    return AttributeValue.builder().s(v).build();
  }

  private Map<String, AttributeValue> key(String id) {
    return Map.of("pk", s(partition), "sk", s(id));
  }

  private void put(String id, Object value) {
    Map<String, AttributeValue> item = new HashMap<>(key(id));
    item.put("payload", s(new String(Json.bytes(value), java.nio.charset.StandardCharsets.UTF_8)));
    client.putItem(r -> r.tableName(table).item(item));
  }

  private <T> Optional<T> get(String id, Class<T> type) {
    var item = client.getItem(r -> r.tableName(table).key(key(id)).consistentRead(true)).item();
    return item.isEmpty()
        ? Optional.empty()
        : Optional.of(
            Json.read(
                item.get("payload").s().getBytes(java.nio.charset.StandardCharsets.UTF_8), type));
  }

  private <T> List<T> query(String prefix, Class<T> type) {
    List<T> result = new ArrayList<>();
    var request =
        QueryRequest.builder()
            .tableName(table)
            .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
            .expressionAttributeValues(Map.of(":pk", s(partition), ":prefix", s(prefix)))
            .scanIndexForward(false)
            .consistentRead(true)
            .limit(500)
            .build();
    for (var page : client.queryPaginator(request))
      for (var item : page.items()) {
        result.add(
            Json.read(
                item.get("payload").s().getBytes(java.nio.charset.StandardCharsets.UTF_8), type));
        if (result.size() >= 500) return result;
      }
    return result;
  }

  public void save(Job j) {
    put("JOB#" + j.id, j);
  }

  public void save(Backup b) {
    put("BACKUP#" + b.id, b);
  }

  public Optional<Job> job(String id) {
    return get("JOB#" + id, Job.class);
  }

  public Optional<Backup> backup(String id) {
    return get("BACKUP#" + id, Backup.class);
  }

  public List<Job> jobs() {
    return query("JOB#", Job.class);
  }

  public List<Backup> backups() {
    return query("BACKUP#", Backup.class);
  }

  public boolean acquire(String owner) {
    long now = Instant.now().getEpochSecond();
    Map<String, AttributeValue> item = new HashMap<>(key("LOCK"));
    item.put("owner", s(owner));
    item.put("expiresAt", AttributeValue.builder().n(Long.toString(now + 960)).build());
    try {
      client.putItem(
          r ->
              r.tableName(table)
                  .item(item)
                  .conditionExpression("attribute_not_exists(pk) OR expiresAt < :now")
                  .expressionAttributeValues(
                      Map.of(":now", AttributeValue.builder().n(Long.toString(now)).build())));
      return true;
    } catch (ConditionalCheckFailedException e) {
      return false;
    }
  }

  public void release(String owner) {
    try {
      client.deleteItem(
          r ->
              r.tableName(table)
                  .key(key("LOCK"))
                  .conditionExpression("#owner = :owner")
                  .expressionAttributeNames(Map.of("#owner", "owner"))
                  .expressionAttributeValues(Map.of(":owner", s(owner))));
    } catch (ConditionalCheckFailedException ignored) {
      /* Another lease owns this lock. */
    }
  }

  public void save(Account account) {
    put("ACCOUNT#" + account.id, account);
  }

  public Optional<Account> account(String id) {
    return get("ACCOUNT#" + id, Account.class);
  }

  public List<Account> accounts() {
    return query("ACCOUNT#", Account.class);
  }

  public Repository forTenant(String id) {
    if (!id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid workspace ID");
    return new DynamoRepository(client, table, "TENANT#" + id);
  }
}
