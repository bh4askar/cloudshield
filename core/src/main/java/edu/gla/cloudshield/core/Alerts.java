package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.Job;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class Alerts {
  private final Config config;

  public Alerts(Config config, Object unused) {
    this.config = config;
  }

  public void send(Job job) {
    boolean delivered = false;
    StringBuilder errors = new StringBuilder();
    if (!config.webhookUrl().isBlank())
      try {
        var request =
            HttpRequest.newBuilder(URI.create(config.webhookUrl()))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(Json.bytes(job)))
                .build();
        var response =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw new IllegalStateException("Non-success webhook response");
        delivered = true;
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        errors.append("Webhook delivery failed. ");
      }
    job.notificationStatus =
        errors.isEmpty()
            ? (delivered ? "DELIVERED" : (config.azure() ? "AZURE_LOG" : "LOCAL_LOG"))
            : "FAILED";
    job.notificationError = errors.isEmpty() ? null : errors.toString().trim();
    System.out.println(
        "CloudShield alert: "
            + new String(Json.bytes(job), java.nio.charset.StandardCharsets.UTF_8));
  }
}
