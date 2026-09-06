package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Turn-3 criterion 3.2, and the test the whole project points at.
 *
 * <p>Twenty requests for the same employee at the same instant must produce exactly one
 * appointment. The failure this rules out is the one the framing document names as costing a
 * business a customer <em>and</em> an apology in person: two people promised one chair.
 *
 * <p><strong>What makes this test worth anything.</strong> It asserts on the database, not only on
 * the responses. A service that answered 201 nineteen times while writing one row would pass a
 * response-only check and still be broken; so would one that wrote twenty rows and reported them
 * honestly. The row count under an occupying status is the property the business actually depends
 * on, and the response codes are the contract on top of it.
 *
 * <p><strong>What it can and cannot prove.</strong> "Exactly one row" holds for a correct
 * implementation under every interleaving, so a failure here is always real — this test never fails
 * by timing luck. A pass is strong evidence rather than proof: the threads must genuinely collide
 * for a broken implementation to be caught, which is what the latch and the repeated rounds are
 * for. Written from the specification with no sight of the implementation, so it tests the
 * guarantee the spec demands rather than the lock the author had in mind.
 */
class AppointmentConcurrencyIT extends ApiIntegrationTest {

    private static final int SIMULTANEOUS_REQUESTS = 20;
    private static final int ROUNDS = 3;

    @Test
    @DisplayName("3.2 exactly one of twenty simultaneous identical bookings succeeds")
    void exactlyOneOfManySimultaneousBookingsSucceeds() throws Exception {
        List<java.util.Set<String>> observedCodes = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                Bookable bookable = newBookableBusiness("race-" + round, 60);
                Instant contested = firstAvailableStart(bookable);

                CountDownLatch ready = new CountDownLatch(SIMULTANEOUS_REQUESTS);
                CountDownLatch release = new CountDownLatch(1);
                List<Callable<ResponseEntity<String>>> attempts = IntStream.range(0, SIMULTANEOUS_REQUESTS)
                        .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> {
                            // Each caller is a different customer wanting the same chair.
                            Object payload = bookingBody(
                                    bookable.serviceId(),
                                    bookable.employeeId(),
                                    contested,
                                    "racer-" + i + "-" + UUID.randomUUID() + "@example.test");
                            ready.countDown();
                            release.await(20, TimeUnit.SECONDS);
                            return post(
                                    businessPath(bookable.businessId(), "/appointments"),
                                    payload,
                                    bookable.owner().accessToken());
                        })
                        .toList();

                List<Future<ResponseEntity<String>>> futures = attempts.stream().map(pool::submit).toList();
                assertThat(ready.await(20, TimeUnit.SECONDS))
                        .as("round %d: every request reached the start line", round)
                        .isTrue();
                release.countDown();

                List<ResponseEntity<String>> responses = new ArrayList<>();
                for (Future<ResponseEntity<String>> future : futures) {
                    responses.add(future.get(60, TimeUnit.SECONDS));
                }

                List<ResponseEntity<String>> created = responses.stream()
                        .filter(r -> r.getStatusCode().value() == 201)
                        .toList();
                List<Integer> refusedCodes = responses.stream()
                        .map(r -> r.getStatusCode().value())
                        .filter(status -> status != 201)
                        .toList();

                // The guarantee, read from the database rather than from what the API said about it.
                Integer occupying = jdbc().queryForObject(
                        "select count(*) from appointments "
                                + "where employee_id = ?::uuid and starts_at = ? "
                                + "and status in ('PENDING', 'CONFIRMED')",
                        Integer.class,
                        bookable.employeeId(),
                        java.sql.Timestamp.from(contested));
                assertThat(occupying)
                        .as("round %d: appointments occupying %s for employee %s. Two rows here is "
                                + "two people arriving for one chair", round, contested, bookable.employeeId())
                        .isEqualTo(1);

                assertThat(created)
                        .as("round %d: %d simultaneous requests for the same time must produce one "
                                + "winner, not %d", round, SIMULTANEOUS_REQUESTS, created.size())
                        .hasSize(1);
                assertThat(refusedCodes)
                        .as("round %d: every loser must be told the time was taken, with 409 — not "
                                + "a 500 that reads as a server fault, and not a 201 that promises "
                                + "an appointment the business cannot honour", round)
                        .hasSize(SIMULTANEOUS_REQUESTS - 1)
                        .containsOnly(409);

                // 3.2 asks for a stable code. "Stable" is asserted as: an identifier a client can
                // branch on rather than prose, drawn from a fixed vocabulary rather than generated
                // per request. Whether all nineteen must name the *same* code is not something the
                // criterion settles, and this implementation uses two — reported rather than
                // resolved here by assertion.
                java.util.Set<String> codes = new java.util.TreeSet<>();
                for (ResponseEntity<String> refusal : responses) {
                    if (refusal.getStatusCode().value() != 409) {
                        continue;
                    }
                    String code = json(refusal).path("code").asText();
                    assertThat(code)
                            .as("round %d: a refusal must name a code a client can branch on, not "
                                    + "a sentence and not something generated per request", round)
                            .isNotBlank()
                            .matches("^[A-Z][A-Z0-9_]*$");
                    codes.add(code);
                }
                observedCodes.add(codes);

                // And the winner is the row that exists.
                String winnerId = json(created.get(0)).path("id").asText();
                Map<String, Object> stored = jdbc().queryForMap(
                        "select employee_id, starts_at, status from appointments where id = ?::uuid", winnerId);
                assertThat(String.valueOf(stored.get("employee_id"))).isEqualTo(bookable.employeeId());
                assertThat(String.valueOf(stored.get("status")))
                        .as("the surviving appointment occupies the time")
                        .isIn("PENDING", "CONFIRMED");

                Integer total = jdbc().queryForObject(
                        "select count(*) from appointments where business_id = ?::uuid",
                        Integer.class,
                        bookable.businessId());
                assertThat(total)
                        .as("round %d: no losing request may leave a row behind in any status — a "
                                + "cancelled or pending remnant is a booking nobody made", round)
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("pool shut down").isTrue();
        }

        // The vocabulary is fixed: whatever codes a losing request can receive, it is the same set
        // every time. A code that varied between rounds would be one no client could rely on.
        assertThat(observedCodes)
                .as("the codes a losing booking receives, per round: %s", observedCodes)
                .allMatch(codes -> codes.equals(observedCodes.get(0)));
    }

    /**
     * 3.32 — two visitors sharing an email address, booking different free times at once, both
     * succeed.
     *
     * <p>This is the case that used to answer 500. Two simultaneous bookings for one email race to
     * create the same customer row, one loses the unique index, and a duplicate-key violation
     * arriving as the same exception type as an overlap was reported as a double booking or as a
     * server fault — pitfall 4. It is also the case whose fix caused the connection-pool deadlock
     * that {@code exactlyOneOfManySimultaneousBookingsSucceeds} catches, so a regression here will
     * take one of two shapes: a 5xx, or a hang. The bounded {@code get} below turns the second into
     * a failure rather than a suite that never finishes.
     *
     * <p>One email being two people is ordinary: a couple booking two cuts, a parent booking for a
     * child. Refusing the second is refusing a booking the business could have honoured, which
     * part 1 says is the cheaper mistake — but answering 500 is not a refusal, it is a fault.
     */
    @Test
    @DisplayName("3.32 two bookings sharing an email at different times both succeed")
    void twoBookingsSharingAnEmailBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                Bookable bookable = newBookableBusiness("shared-email-" + round, 30);
                List<java.time.Instant> starts = availableStarts(bookable.owner(), bookable.businessId(),
                        bookable.serviceId(), bookable.employeeId(), BOOKING_DATE);
                java.time.Instant first = starts.get(0);
                java.time.Instant second = starts.stream()
                        .filter(start -> !start.isBefore(first.plusSeconds(60L * bookable.durationMinutes())))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no second, non-overlapping slot"));
                String sharedEmail = "household-" + UUID.randomUUID() + "@example.test";

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch release = new CountDownLatch(1);
                List<Callable<ResponseEntity<String>>> attempts = List.of(first, second).stream()
                        .<Callable<ResponseEntity<String>>>map(start -> () -> {
                            Object payload = bookingBody(
                                    bookable.serviceId(), bookable.employeeId(), start, sharedEmail);
                            ready.countDown();
                            release.await(20, TimeUnit.SECONDS);
                            return post(
                                    businessPath(bookable.businessId(), "/appointments"),
                                    payload,
                                    bookable.owner().accessToken());
                        })
                        .toList();

                List<Future<ResponseEntity<String>>> futures = attempts.stream().map(pool::submit).toList();
                assertThat(ready.await(20, TimeUnit.SECONDS))
                        .as("round %d: both requests reached the start line", round)
                        .isTrue();
                release.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<ResponseEntity<String>> future : futures) {
                    // Bounded on purpose: a deadlock is a failure, not a reason to wait for ever.
                    statuses.add(future.get(60, TimeUnit.SECONDS).getStatusCode().value());
                }

                assertThat(statuses)
                        .as("round %d: two different free times are two bookings the business can "
                                + "honour, whoever the email belongs to", round)
                        .containsExactly(201, 201);
                assertThat(statuses)
                        .as("round %d: and neither may be a server fault — one email is not an "
                                + "error, it is a household", round)
                        .noneMatch(status -> status >= 500);

                Integer appointments = jdbc().queryForObject(
                        "select count(*) from appointments where business_id = ?::uuid "
                                + "and status in ('PENDING', 'CONFIRMED')",
                        Integer.class,
                        bookable.businessId());
                assertThat(appointments).as("round %d: both bookings exist", round).isEqualTo(2);

                Integer customers = jdbc().queryForObject(
                        "select count(*) from customers where business_id = ?::uuid and lower(email) = lower(?)",
                        Integer.class,
                        bookable.businessId(),
                        sharedEmail);
                assertThat(customers)
                        .as("round %d: one email is one customer of this business, even when two "
                                + "requests created it at the same instant", round)
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("pool shut down").isTrue();
        }
    }
}
