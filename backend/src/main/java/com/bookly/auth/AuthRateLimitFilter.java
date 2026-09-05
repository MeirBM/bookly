package com.bookly.auth;

import com.bookly.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A fixed-window request cap on the unauthenticated authentication endpoints.
 *
 * <p>Without it, {@code POST /api/auth/login} answers credential-stuffing attempts as fast as the
 * server can hash — and credential stuffing does not guess, it replays passwords the user already
 * chose elsewhere, so a 12-character minimum does not help. Registration is equally open, which
 * makes unlimited account creation free.
 *
 * <p>This is the first real use of Redis in the project, and the reason it is in the stack: the
 * count has to be shared across instances, and it is throwaway state that may be lost without harm.
 * PostgreSQL stays the source of truth for everything that matters.
 *
 * <p><strong>Fails open.</strong> If Redis is unreachable the request proceeds, with a warning. The
 * alternative — refusing every login while the cache is down — turns a cache outage into a total
 * outage, and this control mitigates an attack rather than protecting correctness. That is a
 * deliberate trade and belongs in the audit, not in a comment nobody reads.
 *
 * <p><strong>Keyed on the socket address.</strong> {@code X-Forwarded-For} is attacker-controlled
 * unless a known proxy sets it, so trusting it here would hand out a trivial bypass header. When
 * this is deployed behind a load balancer, that becomes wrong in the other direction — every
 * request appears to come from the balancer — and this must be revisited together with
 * {@code server.forward-headers-strategy}.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String PROTECTED_PREFIX = "/api/auth/";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final Duration window;

    public AuthRateLimitFilter(StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               @Value("${bookly.security.rate-limit.max-requests:20}") int maxRequests,
                               @Value("${bookly.security.rate-limit.window:PT1M}") Duration window) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String key = "ratelimit:" + request.getRequestURI() + ":" + request.getRemoteAddr();

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
        } catch (RuntimeException ex) {
            log.warn("Rate limiting unavailable, allowing request: {}", ex.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (count != null && count > maxRequests) {
            log.warn("Rate limit exceeded for {} from {}",
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of("RATE_LIMITED", "Too many requests. Try again shortly."));
            return;
        }

        chain.doFilter(request, response);
    }
}
