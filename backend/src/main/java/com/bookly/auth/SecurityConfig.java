package com.bookly.auth;

import com.bookly.business.TenantGuard;
import com.bookly.common.error.ApiError;
import com.bookly.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           AuthenticationEntryPoint entryPoint,
                                           AccessDeniedHandler accessDeniedHandler,
                                           TenantGuard tenantGuard,
                                           CorsConfigurationSource corsConfigurationSource,
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
                // Registered so Spring Security's CorsFilter answers the preflight before
                // authorization runs. Without it the chain replied 401 to every OPTIONS - a
                // preflight carries no credentials by construction - and the browser therefore
                // refused every call the dashboard makes. The API was correct and unreachable.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // The customer-facing surface: no account, by design. Requiring one
                        // is the friction the problem statement objects to. Held to its own
                        // rate limit, and answering from its own DTOs.
                        .requestMatchers("/api/public/**").permitAll()
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
     * Cross-origin access for the browser client.
     *
     * <p>The deployment is cross-origin by design — the frontend and the API are separate services
     * — so this is required rather than a convenience. Origins are listed explicitly and read from
     * configuration: {@code allowedOrigins("*")} would let any site on the internet script this API
     * with a token it obtained, and the wildcard is the shortest thing that appears to work, which
     * is exactly why it gets reached for.
     *
     * <p>Credentials are not allowed, deliberately. Authentication travels in an {@code
     * Authorization} header the client sets, not in a cookie the browser attaches on its own, so
     * there is nothing for a third-party page to ride on and no CSRF surface to defend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${bookly.cors.allowed-origins:http://localhost:3000}") String rawOrigins) {
        List<String> origins = parseOrigins(rawOrigins);
        // Logged because a wrong value here is invisible from the outside: every screen loads and
        // then fails in the browser, while the API answers every request perfectly. One line at
        // startup turns "the site is broken" into "the origin list is not what I thought".
        log.info("CORS allows origins: {}", origins);

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Splits the configured origins, tolerating how deployment platforms actually deliver values.
     *
     * <p>A platform variable arrives as one string, and it commonly arrives with stray quotes or
     * spaces — a raw editor keeps the quotes it should strip, or a value gets pasted with a space
     * after the comma. Binding straight to a list turns any of those into an origin that matches
     * nothing, and the failure is total and silent: every browser call is refused while the API
     * itself is healthy, so the frontend looks broken and the backend looks fine.
     *
     * <p>Trimming is normalisation, not leniency. An origin that differs by scheme, host or port is
     * still refused, which is the property that matters.
     */
    private static List<String> parseOrigins(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(origin -> origin.replaceAll("^[\"']+|[\"']+$", ""))
                .map(origin -> origin.endsWith("/")
                        ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isBlank())
                .toList();
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
            // Generic, because this handler covers every authorization rule, not only the tenant
            // one. Labelling a refused /v3/api-docs request BUSINESS_ACCESS_DENIED told a client
            // to branch on a code that had nothing to do with what it asked for.
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiError.of("ACCESS_DENIED", "You do not have access to this resource."));
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
