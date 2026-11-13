package edu.gla.cloudshield.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.gla.cloudshield.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantApiTest {
  @Autowired RuntimeContext base;
  @Autowired MockMvc mvc;

  @Test
  void signedInMemberCannotReadAnotherMembersBackupOrJob() throws Exception {
    var alice =
        GoogleIdentityTest.user(
            "api-alice", "alice@gmail.com", true, "https://accounts.google.com");
    var bob =
        GoogleIdentityTest.user("api-bob", "bob@gmail.com", true, "https://accounts.google.com");
    String aliceId = GoogleIdentity.workspaceId(alice), bobId = GoogleIdentity.workspaceId(bob);
    for (var u : new org.springframework.security.oauth2.core.oidc.user.OidcUser[] {alice, bob}) {
      var account = new Models.Account();
      account.id = GoogleIdentity.workspaceId(u);
      account.email = u.getEmail();
      account.name = u.getFullName();
      account.createdAt = java.time.Instant.now().toString();
      base.repository().save(account);
      var scope = base.forTenant(account.id);
      SampleData.seed(scope.source());
      scope.engine().execute(scope.engine().createJob("BACKUP", null, "TEST").id);
    }
    String backup = base.forTenant(aliceId).repository().backups().getFirst().id;
    mvc.perform(get("/api/overview").with(oidcLogin().oidcUser(bob)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.id").value(bobId));
    mvc.perform(get("/api/jobs/" + backup).with(oidcLogin().oidcUser(bob)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/jobs")
                .with(oidcLogin().oidcUser(bob))
                .with(csrf())
                .contentType("application/json")
                .content("{\"type\":\"RESTORE\",\"backupId\":\"" + backup + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/sample").param("restoreJobId", backup).with(oidcLogin().oidcUser(bob)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/report").with(oidcLogin().oidcUser(bob)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(backup))));
  }
}
