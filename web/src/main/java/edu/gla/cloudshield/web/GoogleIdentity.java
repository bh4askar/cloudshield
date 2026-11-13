package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.BackupEngine;
import java.nio.charset.StandardCharsets;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class GoogleIdentity {
  private GoogleIdentity() {}

  public static org.springframework.security.oauth2.client.web
          .DefaultOAuth2AuthorizationRequestResolver
      authorizationResolver(
          org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
              registrations) {
    var resolver =
        new org.springframework.security.oauth2.client.web
            .DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(
        builder ->
            builder.additionalParameters(parameters -> parameters.put("prompt", "select_account")));
    return resolver;
  }

  public static boolean enabled() {
    return !System.getenv().getOrDefault("GOOGLE_CLIENT_ID", "").isBlank()
        && !System.getenv().getOrDefault("GOOGLE_CLIENT_SECRET", "").isBlank();
  }

  public static String workspaceId(OidcUser user) {
    String issuer = user.getIssuer() == null ? "" : user.getIssuer().toString();
    if (!issuer.equals("https://accounts.google.com")
        || !Boolean.TRUE.equals(user.getEmailVerified())
        || user.getEmail() == null
        || user.getEmail().isBlank()
        || user.getSubject() == null
        || user.getSubject().isBlank()) {
      throw new OAuth2AuthenticationException("A verified Google identity is required");
    }
    return BackupEngine.sha((issuer + "|" + user.getSubject()).getBytes(StandardCharsets.UTF_8))
        .substring(0, 32);
  }
}
