package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The verification semantics an access token has to have, as a unit test with no Spring context
 * (spec part 4: "token creation, expiry and signature verification").
 *
 * <p>This pins the contract that criterion 1.8 rests on — an {@code exp} in the past must fail, a
 * signature made with any other key must fail, an unsigned token must fail, and a token whose
 * payload was edited after signing must fail. It deliberately names no class from
 * {@code backend/src/main}; whether the application's own filter actually enforces this is decided
 * over HTTP by {@code AuthFlowIT.expiredAccessTokenRejected}.
 */
class AccessTokenContractTest {

    private static final SecretKey KEY = key("test-only-secret-not-used-anywhere-else-0123456789abcdef");
    private static final SecretKey OTHER_KEY = key("a-different-secret-of-at-least-thirty-two-bytes-0123456789");

    private static SecretKey key(String secret) {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(SecretKey signingKey, Instant issuedAt, Duration ttl) {
        return Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .issuer("bookly")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("a token that has not expired verifies against the issuing key")
    void validTokenVerifies() {
        String jwt = token(KEY, Instant.now(), Duration.ofMinutes(15));

        Claims claims = Jwts.parser().verifyWith(KEY).build().parseSignedClaims(jwt).getPayload();

        assertThat(claims.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(claims.getExpiration()).isAfter(Date.from(Instant.now()));
    }

    @Test
    @DisplayName("a token whose exp has passed is rejected, however well it is signed")
    void expiredTokenIsRejected() {
        String jwt = token(KEY, Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(15));

        assertThatThrownBy(() -> Jwts.parser().verifyWith(KEY).build().parseSignedClaims(jwt))
                .as("an access token issued two hours ago with a 15 minute lifetime")
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void tokenSignedWithAnotherKeyIsRejected() {
        String jwt = token(OTHER_KEY, Instant.now(), Duration.ofMinutes(15));

        assertThatThrownBy(() -> Jwts.parser().verifyWith(KEY).build().parseSignedClaims(jwt))
                .as("a token minted by someone who does not hold the signing key")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an unsigned token is rejected rather than trusted")
    void unsignedTokenIsRejected() {
        String jwt = Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                .compact();

        assertThatThrownBy(() -> Jwts.parser().verifyWith(KEY).build().parseSignedClaims(jwt))
                .as("alg=none must never be accepted")
                .isInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    @DisplayName("a token whose payload was edited after signing is rejected")
    void tamperedTokenIsRejected() {
        String jwt = token(KEY, Instant.now(), Duration.ofMinutes(15));
        String[] parts = jwt.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"22222222-2222-2222-2222-222222222222\",\"iss\":\"bookly\",\"exp\":"
                                + (Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond() + "}"))
                        .getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> Jwts.parser().verifyWith(KEY).build().parseSignedClaims(tampered))
                .as("swapping the subject must invalidate the signature")
                .isInstanceOf(JwtException.class);
    }
}
