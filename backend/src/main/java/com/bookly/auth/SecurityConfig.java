package com.bookly.auth;

import com.bookly.business.TenantGuard;
import com.bookly.common.error.ApiError;
import com.bookly.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
// The OpenAPI document described every route's status codes but never said how a caller
// authenticates, so a client had to guess the scheme. Declaring it completes the contract
// and lets generated clients and Swagger UI send a token.
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
// Without this, every @PreAuthorize in the project is decorative and TenantGuard is never
// consulted. Turn-1 spec, pitfall 1 — the failure is silent, which is why it is named.
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           AuthenticationEntryPoint entryPoint,
                                           AccessDeniedHandler accessDeniedHandler,
                                           TenantGuard tenantGuard,
                                           @Value("${bookly.security.expose-api-docs:false}")
                                           boolean exposeApiDocs) throws Exception {
        String[] apiDocPaths = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

        // Tenant access, decided here rather than by @PreAuthorize so it happens before Spring
        // resolves handler arguments. A missing query parameter used to produce 400 for a caller
        // who was never entitled to the business, which let an outsider probe the endpoint's
        // shape. A malformed business id denies rather than throwing: it cannot name a business
        // the caller belongs to.
        AuthorizationManager<RequestAuthorizationContext> tenantAccess = (authentication, context) -> {
            String raw = context.getVariables().get("businessId");
            try {
                return new AuthorizationDecision(
                        tenantGuard.canAccess(authentication.get(), UUID.fromString(raw)));
            } catch (IllegalArgumentException | NullPointerException ex) {
                return new AuthorizationDecision(false);
            }
        };
        return http
                // No cookies are used, so there is no cookie for a third-party site to ride on.
                // The same API must serve Android and iOS clients that have no cookie jar.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Both shapes: the business itself, and everything under it.
                        .requestMatchers("/api/businesses/{businessId}").access(tenantAccess)
                        .requestMatchers("/api/businesses/{businessId}/**").access(tenantAccess)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // The document is a complete map of the API surface. Criterion 1.16
                        // requires it to exist and be complete, not to be public.
                        .requestMatchers(apiDocPaths)
                            .access((authentication, context) -> new AuthorizationDecision(
                                    exposeApiDocs))
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Returns the project's error shape for an unauthenticated request, not Spring's default. */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiError.of("UNAUTHENTICATED", "Authentication is required."));
        };
    }

    /**
     * Writes the refusal directly instead of calling {@code sendError}.
     *
     * <p>Spring's default handler calls {@code sendError}, which asks the container to dispatch the
     * request again as an ERROR. The security chain then runs a second time — and every
     * {@code OncePerRequestFilter} correctly declines to run twice, so the JWT filter does not
     * repopulate the context. The second pass therefore looks anonymous, and
     * {@code ExceptionTranslationFilter} replaces the correct 403 with a 401 saying authentication
     * is required, to a caller who was perfectly well authenticated and simply not entitled.
     *
     * <p>The symptom is worse than a wrong status: it tells a caller to go and authenticate when
     * doing so cannot possibly help. Writing the response here ends the request where it was
     * decided.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> {
            ApiException denied = ApiException.noBusinessAccess();
            response.setStatus(denied.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiError.of(denied.getCode(), denied.getMessage()));
        };
    }

    /**
     * @param strength overridden only in the test profile, where the default cost would make the
     *                 suite spend most of its time hashing. It must never be lowered elsewhere.
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${bookly.security.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    /** Injected wherever time is read, so expiry is testable without sleeping. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
