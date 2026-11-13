package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.Job;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import software.amazon.awssdk.services.sns.SnsClient;

public class Alerts {
  private final Config config;
  private final SnsClient sns;

  public Alerts(Config config, SnsClient sns) {
    this.config = config;
    this.sns = sns;
  }

  public void send(Job job) {
    boolean delivered = false;
    StringBuilder errors = new StringBuilder();
    if (!config.topicArn().isBlank())
      try {
        sns.publish(
            r ->
                r.topicArn(config.topicArn())
                    .subject("CloudShield " + job.type + " " + job.status)
                    .message(new String(Json.bytes(job), java.nio.charset.StandardCharsets.UTF_8)));
        delivered = true;
      } catch (Exception e) {
        errors.append("SNS delivery failed. ");
      }
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
    job.notificationStatus = errors.isEmpty() ? (delivered ? "DELIVERED" : "LOCAL_LOG") : "FAILED";
    job.notificationError = errors.isEmpty() ? null : errors.toString().trim();
    System.out.println(
        "CloudShield alert: "
            + new String(Json.bytes(job), java.nio.charset.StandardCharsets.UTF_8));
  }
}
