package com.bookly.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Locks the row for the duration of the caller's transaction.
     *
     * <p>The lock is the reuse check, not an optimisation. Without it, two refreshes presenting the
     * same token race: both read {@code revoked_at IS NULL}, both pass the check in Java, and both
     * mint a successor. The two holders then rotate down separate branches of one family and never
     * present each other's spent token, so reuse is never detected again and a stolen token yields
     * an undetected session for the full refresh lifetime. A sequential test cannot see this.
     *
     * <p>With {@code FOR UPDATE} the second transaction blocks at the read, and on acquiring the
     * lock re-reads a row whose {@code revoked_at} is now set — turning the attacker's race from a
     * bypass into exactly the detection this design was reaching for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Unlocked read for paths that only look, such as logout. */
    Optional<RefreshToken> findFirstByTokenHash(String tokenHash);

    /**
     * Revokes every unrevoked token in a family in one statement.
     *
     * <p>Used when a rotated token is presented again: the presenter and the legitimate holder
     * cannot be told apart, so neither is allowed to continue.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
