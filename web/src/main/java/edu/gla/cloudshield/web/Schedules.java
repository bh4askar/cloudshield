package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.RuntimeContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Schedules {
  private final RuntimeContext runtime;
  private final JobDispatcher dispatcher;

  public Schedules(RuntimeContext runtime, JobDispatcher dispatcher) {
    this.runtime = runtime;
    this.dispatcher = dispatcher;
  }

  @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
  public void daily() {
    submitAll("BACKUP");
  }

  @Scheduled(cron = "0 30 2 * * SUN", zone = "Asia/Kolkata")
  public void weekly() {
    submitAll("TEST");
  }

  private void submitAll(String type) {
    if (runtime.config().azure()) return;
    dispatcher.submit(type, null, "SCHEDULE");
    for (var account : runtime.repository().accounts()) {
      try {
        dispatcher.submit(runtime.forTenant(account.id), account.id, type, null, "SCHEDULE");
      } catch (Exception failure) {
        System.err.println("Schedule dispatch failed for workspace " + account.id);
      }
    }
  }
}
