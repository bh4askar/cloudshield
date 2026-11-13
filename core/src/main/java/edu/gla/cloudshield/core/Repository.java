package edu.gla.cloudshield.core;

import edu.gla.cloudshield.core.Models.*;
import java.util.List;
import java.util.Optional;

public interface Repository {
  default Models.Job create(Models.Job job) {
    synchronized (this) {
      var existing = job(job.id);
      if (existing.isPresent()) return existing.get();
      save(job);
      return job;
    }
  }

  void save(Job job);

  void save(Backup backup);

  Optional<Job> job(String id);

  Optional<Backup> backup(String id);

  List<Job> jobs();

  List<Backup> backups();

  boolean acquire(String owner);

  void release(String owner);

  void save(Account account);

  Optional<Account> account(String id);

  List<Account> accounts();

  Repository forTenant(String id);
}
