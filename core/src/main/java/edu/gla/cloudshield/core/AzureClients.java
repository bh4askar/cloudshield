package edu.gla.cloudshield.core;

import com.azure.core.credential.TokenCredential;
import com.azure.data.tables.*;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.*;
import com.azure.storage.queue.*;
import com.azure.storage.queue.QueueMessageEncoding;

public final class AzureClients {
  private static final TokenCredential CREDENTIAL = new DefaultAzureCredentialBuilder().build();

  private AzureClients() {}

  public static BlobContainerClient container(String account, String container) {
    return new BlobContainerClientBuilder()
        .endpoint("https://" + account + ".blob.core.windows.net")
        .containerName(container)
        .credential(CREDENTIAL)
        .buildClient();
  }

  public static TableClient table(Config c) {
    return new TableClientBuilder()
        .endpoint("https://" + c.primaryAccount() + ".table.core.windows.net")
        .tableName(c.table())
        .credential(CREDENTIAL)
        .buildClient();
  }

  public static QueueClient queue(Config c) {
    return new QueueClientBuilder()
        .endpoint("https://" + c.recoveryAccount() + ".queue.core.windows.net")
        .queueName(c.queue())
        .credential(CREDENTIAL)
        .messageEncoding(QueueMessageEncoding.BASE64)
        .buildClient();
  }
}
