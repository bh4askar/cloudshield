package edu.gla.cloudshield.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
  @Autowired MockMvc mvc;

  @Test
  void unauthenticatedApiIsDenied() throws Exception {
    mvc.perform(get("/api/overview")).andExpect(status().isUnauthorized());
  }

  @Test
  void loginAndCsrfTokenArePublic() throws Exception {
    mvc.perform(get("/login.html")).andExpect(status().isOk());
    mvc.perform(get("/api/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  @WithMockUser
  void authenticatedDashboardReturnsRealData() throws Exception {
    mvc.perform(get("/api/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("local"));
    mvc.perform(get("/api/sample"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrations").isArray());
  }

  @Test
  @WithMockUser
  void mutationWithoutCsrfIsDenied() throws Exception {
    mvc.perform(post("/api/jobs").contentType("application/json").content("{\"type\":\"BACKUP\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void invalidMutationIsRejected() throws Exception {
    mvc.perform(
            post("/api/jobs")
                .with(csrf())
                .contentType("application/json")
                .content("{\"type\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void securityHeadersArePresent() throws Exception {
    mvc.perform(get("/"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }
}
