package edu.gla.cloudshield.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class GoogleIdentityTest {
  static DefaultOidcUser user(String subject, String email, boolean verified, String issuer) {
    var claims = new HashMap<String, Object>();
    claims.put("sub", subject);
    claims.put("iss", issuer);
    claims.put("email", email);
    claims.put("email_verified", verified);
    claims.put("name", "Test Member");
    return new DefaultOidcUser(
        List.of(new SimpleGrantedAuthority("OIDC_USER")),
        new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(3600), claims));
  }

  @Test
  void anyVerifiedGoogleEmailCanHaveAnAccount() {
    assertEquals(
        32,
        GoogleIdentity.workspaceId(
                user("1001", "member@gmail.com", true, "https://accounts.google.com"))
            .length());
    assertEquals(
        32,
        GoogleIdentity.workspaceId(
                user("1002", "member@college.example", true, "https://accounts.google.com"))
            .length());
  }

  @Test
  void unverifiedEmailAndWrongIssuerAreRejected() {
    assertThrows(
        OAuth2AuthenticationException.class,
        () ->
            GoogleIdentity.workspaceId(
                user("1001", "member@gmail.com", false, "https://accounts.google.com")));
    assertThrows(
        OAuth2AuthenticationException.class,
        () ->
            GoogleIdentity.workspaceId(
                user("1001", "member@gmail.com", true, "https://untrusted.example")));
  }

  @Test
  void workspaceIdentityUsesSubjectInsteadOfChangeableEmail() {
    assertEquals(
        GoogleIdentity.workspaceId(
            user("1001", "old@gmail.com", true, "https://accounts.google.com")),
        GoogleIdentity.workspaceId(
            user("1001", "new@gmail.com", true, "https://accounts.google.com")));
    assertNotEquals(
        GoogleIdentity.workspaceId(
            user("1001", "same@gmail.com", true, "https://accounts.google.com")),
        GoogleIdentity.workspaceId(
            user("1002", "same@gmail.com", true, "https://accounts.google.com")));
  }

  @Test
  void authorizationFlowRequestsAccountChooserAndKeepsState() {
    var registration =
        org.springframework.security.config.oauth2.client.CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId("test-client")
            .clientSecret("test-secret")
            .scope("openid", "profile", "email")
            .build();
    var resolver =
        GoogleIdentity.authorizationResolver(
            new org.springframework.security.oauth2.client.registration
                .InMemoryClientRegistrationRepository(registration));
    var request =
        new org.springframework.mock.web.MockHttpServletRequest(
            "GET", "/oauth2/authorization/google");
    request.setServletPath("/oauth2/authorization/google");
    var authorization = resolver.resolve(request);
    assertNotNull(authorization);
    assertEquals("select_account", authorization.getAdditionalParameters().get("prompt"));
    assertNotNull(authorization.getState());
    assertTrue(authorization.getScopes().contains("openid"));
    assertEquals(
        "https://accounts.google.com/o/oauth2/v2/auth", authorization.getAuthorizationUri());
  }
}
