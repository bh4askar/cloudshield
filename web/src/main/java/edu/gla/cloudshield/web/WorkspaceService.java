package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.*;
import edu.gla.cloudshield.core.Models.Account;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
  public record Workspace(String id, String name, String email, RuntimeContext runtime) {}

  private final RuntimeContext base;
  private final JobDispatcher dispatcher;
  private final ConcurrentHashMap<String, RuntimeContext> scopes = new ConcurrentHashMap<>();

  public WorkspaceService(RuntimeContext base, JobDispatcher dispatcher) {
    this.base = base;
    this.dispatcher = dispatcher;
  }

  public synchronized Workspace current(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated())
      throw new IllegalArgumentException("Sign in required");
    if (!(authentication.getPrincipal() instanceof OidcUser user))
      return new Workspace("demo", "Project administrator", "", base);
    String id = GoogleIdentity.workspaceId(user);
    RuntimeContext runtime = scopes.computeIfAbsent(id, base::forTenant);
    if (base.repository().account(id).isEmpty()) {
      // Protect a small teaching deployment from unbounded automatic provisioning.
      int limit = Integer.parseInt(System.getenv().getOrDefault("MAX_ACCOUNTS", "100"));
      if (base.repository().accounts().size() >= limit)
        throw new IllegalArgumentException(
            "New-account capacity has been reached. Contact the site operator.");
      Account account = new Account();
      account.id = id;
      account.name = user.getFullName() == null ? "CloudShield member" : user.getFullName();
      account.email = user.getEmail();
      account.createdAt = Instant.now().toString();
      base.repository().save(account);
    }
    if (runtime.repository().jobs().isEmpty())
      dispatcher.submit(runtime, id, "SETUP", null, "ONBOARDING");
    Account account = base.repository().account(id).orElseThrow();
    return new Workspace(id, account.name, account.email, runtime);
  }
}
