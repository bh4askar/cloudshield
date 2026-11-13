package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.RuntimeContext;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  UserDetailsService users(RuntimeContext runtime) {
    String password = System.getenv().getOrDefault("CLOUDSHIELD_PASSWORD", "");
    if (password.isBlank() && runtime.config().azure())
      throw new IllegalStateException(
          "Set CLOUDSHIELD_PASSWORD to a unique password of at least 12 characters in Azure mode");
    if (runtime.config().azure()
        && (password.equals("CloudShield-Demo-2026!") || password.startsWith("replace-")))
      throw new IllegalStateException(
          "Replace the demo or example password before using Azure mode");
    if (password.isBlank()) password = "CloudShield-Demo-2026!";
    if (password.length() < 12)
      throw new IllegalStateException("CLOUDSHIELD_PASSWORD must contain at least 12 characters");
    return new InMemoryUserDetailsManager(
        User.withUsername(System.getenv().getOrDefault("CLOUDSHIELD_USERNAME", "admin"))
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
            .roles("ADMIN")
            .build());
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, WorkspaceService workspaces) throws Exception {
    String googleId = System.getenv().getOrDefault("GOOGLE_CLIENT_ID", "");
    String googleSecret = System.getenv().getOrDefault("GOOGLE_CLIENT_SECRET", "");
    if (googleId.isBlank() != googleSecret.isBlank())
      throw new IllegalStateException("Set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET");
    if (GoogleIdentity.enabled()) {
      var registration =
          CommonOAuth2Provider.GOOGLE
              .getBuilder("google")
              .clientId(googleId)
              .clientSecret(googleSecret)
              .scope("openid", "profile", "email")
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .build();
      var registrations = new InMemoryClientRegistrationRepository(registration);
      var delegate = new OidcUserService();
      http.oauth2Login(
          o ->
              o.loginPage("/login.html")
                  .clientRegistrationRepository(registrations)
                  .authorizationEndpoint(
                      a ->
                          a.authorizationRequestResolver(
                              GoogleIdentity.authorizationResolver(registrations)))
                  .authorizedClientService(new InMemoryOAuth2AuthorizedClientService(registrations))
                  .userInfoEndpoint(
                      u ->
                          u.oidcUserService(
                              request -> {
                                var user = delegate.loadUser(request);
                                GoogleIdentity.workspaceId(user);
                                return user;
                              }))
                  .successHandler(
                      (request, response, auth) -> {
                        try {
                          workspaces.current(auth);
                          response.sendRedirect("/");
                        } catch (Exception failure) {
                          request.getSession().invalidate();
                          response.sendRedirect("/login.html?account_error");
                        }
                      })
                  .failureHandler(
                      (request, response, failure) -> {
                        // Log diagnostic types/codes only: never tokens, claims or credentials.
                        Throwable cause = failure;
                        for (int depth = 0; cause != null && depth < 8; depth++) {
                          String code =
                              cause
                                      instanceof
                                      org.springframework.security.oauth2.core
                                                  .OAuth2AuthenticationException
                                              oauth
                                  ? oauth.getError().getErrorCode()
                                  : "";
                          org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                              .warn(
                                  "Google sign-in failed: type={}, code={}",
                                  cause.getClass().getSimpleName(),
                                  code);
                          cause = cause.getCause();
                        }
                        response.sendRedirect("/login.html?google_error");
                      }));
    }
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login.html",
                        "/style.css",
                        "/polish.css",
                        "/theme.js",
                        "/login.js",
                        "/favicon.svg",
                        "/privacy.html",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/error",
                        "/health",
                        "/api/csrf",
                        "/public/mode")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login.html")
                    .loginProcessingUrl("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login.html?error")
                    .permitAll())
        .logout(l -> l.logoutSuccessUrl("/login.html?logout"))
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    (request, response, ex) -> response.sendError(401, "Sign in required"),
                    request -> request.getRequestURI().startsWith("/api/")))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; connect-src 'self'; frame-ancestors 'none';"
                                + " base-uri 'self'; form-action 'self'")));
    // Default session-based CSRF protection remains enabled on every mutation, including login.
    return http.build();
  }
}
