package com.bookly.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HMAC signing key. No default anywhere: the application must refuse to start rather
 *               than sign tokens with a key that is committed in this repository.
 */
@ConfigurationProperties(prefix = "bookly.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String issuer) {
}
