package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Turn-1 criteria 1.6 and 1.7.
 *
 * <p>Pitfall 3: refresh rotation without reuse detection is a rotation in name only. A stolen token
 * that still works once leaves theft undetectable, so presenting a rotated token must revoke the
 * whole family — including the token the legitimate client is currently holding.
 *
 * <p>1.7 also requires this to hold when the two refreshes arrive <em>concurrently</em>. The
 * sequential cases below passed while a race defeated detection entirely: both requests read
 * {@code revoked_at IS NULL}, both passed the check, both minted a successor, and the thief and the
 * victim then rotated down separate branches of one family without ever presenting each other's
 * spent token, so detection never fired again.
 */
class RefreshRotationIT extends ApiIntegrationTest {

    private ResponseEntity<String> refresh(String refreshToken) {
        return post("/api/auth/refresh", body("refreshToken", refreshToken));
    }

    /**
     * 1.6 — POST /api/auth/refresh returns a new token pair, and the presented refresh token is
     * thereafter rejected with 401.
     */
    @Test
    @DisplayName("1.6 a rotated refresh token is rejected when it is presented again")
    void rotatedTokenIsRejectedOnReuse() {
        Account account = newAccount("rotate");
        String first = account.refreshToken();

        ResponseEntity<String> rotated = refresh(first);

        assertThat(rotated.getStatusCode().value()).as("first refresh").isEqualTo(200);
        JsonNode pair = json(rotated);
        assertThat(pair.path("accessToken").asText()).as("rotated accessToken").isNotBlank();
        assertThat(pair.path("refreshToken").asText()).as("rotated refreshToken").isNotBlank();
        assertThat(pair.path("refreshToken").asText())
                .as("the refresh token must actually rotate")
                .isNotEqualTo(first);

        ResponseEntity<String> replay = refresh(first);

        assertThat(replay.getStatusCode().value())
                .as("presenting the already-rotated refresh token a second time")
                .isEqualTo(401);
    }

    /**
     * 1.7 — a refresh token reused after rotation revokes every token in <em>the family that token
     * belongs to</em>. A second login starts its own family and must be unaffected: revoking every
     * session a user has because one was replayed would log them out of devices that were never
     * compromised.
     */
    @Test
    @DisplayName("1.7 reusing a rotated refresh token revokes its family and only its family")
    void reuseRevokesFamily() {
        Account account = newAccount("family");
        String compromisedFirst = account.refreshToken();

        // A second, independent login: its own family, on another device.
        ResponseEntity<String> secondLogin = login(account.email(), account.password());
        assertThat(secondLogin.getStatusCode().value()).as("second login").isEqualTo(200);
        String otherFamily = json(secondLogin).path("refreshToken").asText();
        assertThat(otherFamily).as("the second family's token").isNotBlank().isNotEqualTo(compromisedFirst);

        String compromisedSecond = json(refresh(compromisedFirst)).path("refreshToken").asText();
        assertThat(compromisedSecond).as("the rotated token").isNotBlank();

        // The attacker replays the captured, already-rotated token.
        assertThat(refresh(compromisedFirst).getStatusCode().value())
                .as("replay of the captured token")
                .isEqualTo(401);

        // The legitimate holder of that family is now locked out too: that is what "the whole
        // family" means, and it is the only signal the user gets that the token was stolen.
        assertThat(refresh(compromisedSecond).getStatusCode().value())
                .as("the still-current token of the family in which reuse was detected")
                .isEqualTo(401);

        // The other family keeps working — revocation is per family, not per user.
        ResponseEntity<String> unaffected = refresh(otherFamily);
        assertThat(unaffected.getStatusCode().value())
                .as("the token of the family that was never replayed")
                .isEqualTo(200);
        assertThat(json(unaffected).path("refreshToken").asText())
                .as("the unaffected family still rotates normally")
                .isNotBlank();

        Integer liveFamilies = jdbc().queryForObject(
                "select count(distinct family_id) from refresh_tokens "
                        + "where user_id = ?::uuid and revoked_at is null",
                Integer.class,
                account.userId());
        assertThat(liveFamilies)
                .as("families of this user still holding an unrevoked token: the compromised one "
                        + "must be gone and the other must remain")
                .isEqualTo(1);
    }

    /**
     * 1.7, concurrently — firing the same refresh token from many threads at once must leave at
     * most one winner and must not fork the family into two live branches.
     *
     * <p>Note on what this test can and cannot prove. It never fails by timing luck: "at most one
     * success" and "at most one live token in the family" hold for a correct implementation
     * whatever the interleaving, so a failure here is always a real defect. The converse is not
     * true — a pass is evidence, not proof, because the threads may simply not have interleaved on
     * the vulnerable window. Rounds and width are there to make the window likely to be hit, and
     * the check is repeated on a fresh family each round.
     *
     * <p>Nor is "at most one" asserted from below: an implementation that refused all eight would
     * be failing closed, which is correct behaviour, so a round with no winner is not a defect.
     * That leaves the theoretical vacuous pass — an endpoint that refuses everything always — and
     * {@code rotatedTokenIsRejectedOnReuse} above is what rules it out.
     */
    @Test
    @DisplayName("1.7 concurrent refreshes of one token produce at most one successor")
    void concurrentRefreshOfTheSameTokenYieldsAtMostOneSuccessor() throws Exception {
        int rounds = 5;
        int width = 8;
        ExecutorService pool = Executors.newFixedThreadPool(width);
        try {
            for (int round = 1; round <= rounds; round++) {
                Account account = newAccount("race-" + round);
                String contested = account.refreshToken();

                CountDownLatch release = new CountDownLatch(1);
                CountDownLatch ready = new CountDownLatch(width);
                List<Callable<Integer>> attempts = IntStream.range(0, width)
                        .<Callable<Integer>>mapToObj(i -> () -> {
                            ready.countDown();
                            release.await(10, TimeUnit.SECONDS);
                            return refresh(contested).getStatusCode().value();
                        })
                        .toList();

                List<Future<Integer>> futures = attempts.stream().map(pool::submit).toList();
                assertThat(ready.await(10, TimeUnit.SECONDS))
                        .as("every thread reached the start line in round %d", round)
                        .isTrue();
                release.countDown();

                int successes = 0;
                for (Future<Integer> future : futures) {
                    if (future.get(30, TimeUnit.SECONDS) == 200) {
                        successes++;
                    }
                }

                assertThat(successes)
                        .as("round %d: %d threads presented the same refresh token at once; a "
                                + "second success means both passed the revocation check before "
                                + "either wrote, and the family has silently forked", round, width)
                        .isLessThanOrEqualTo(1);

                Integer liveInFamily = jdbc().queryForObject(
                        "select coalesce(max(live), 0) from ("
                                + "  select count(*) as live from refresh_tokens "
                                + "  where user_id = ?::uuid and revoked_at is null group by family_id) f",
                        Integer.class,
                        account.userId());
                assertThat(liveInFamily)
                        .as("round %d: live tokens in the contested family — two is the fork the "
                                + "race produces, and neither holder will ever present the other's "
                                + "spent token, so reuse detection never fires again", round)
                        .isLessThanOrEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).as("pool shut down").isTrue();
        }
    }
}
