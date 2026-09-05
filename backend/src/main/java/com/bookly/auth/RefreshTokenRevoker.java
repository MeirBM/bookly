package com.bookly.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a token family in its own transaction.
 *
 * <p>This exists for one reason, and it is not decomposition for its own sake. Reuse of a rotated
 * token is detected inside {@code AuthService.refresh}, which is transactional and then throws to
 * return 401 — and the throw rolls the revocation back with it. The family stayed live, so a stolen
 * token's replacement kept working and the detection had no effect at all.
 *
 * <p>{@code REQUIRES_NEW} commits the revocation independently of the caller's outcome. A
 * self-invocation would not work here: the proxy is bypassed and the new transaction never starts,
 * which is why this is a separate bean rather than another method on {@code AuthService}.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId, Instant now) {
        return refreshTokenRepository.revokeFamily(familyId, now);
    }
}
