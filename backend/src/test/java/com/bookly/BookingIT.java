package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Turn-3 criteria 3.5, 3.6 and 3.9: what a booking is allowed to be.
 *
 * <p>Pitfall 7 is the theme — the slot in a request arrived from a page that may be minutes old, so
 * the server re-derives availability rather than trusting what it was sent. The exclusion
 * constraint is the last line, not the first.
 */
@TestPropertySource(properties = "bookly.booking.horizon-days=" + BookingIT.HORIZON_DAYS)
class BookingIT extends ApiIntegrationTest {

    /**
     * Set here rather than left to the default, so this suite states its own premise. Wide enough
     * that {@link #BOOKING_DATE}, which every other test in the class books on, stays inside it.
     */
    static final int HORIZON_DAYS = 60;

    private int appointmentCount(String businessId) {
        Integer count = jdbc().queryForObject(
                "select count(*) from appointments where business_id = ?::uuid", Integer.class, businessId);
        return count == null ? 0 : count;
    }

    /** 3.5 — a start the engine never offered is refused, whatever the request says. */
    @Test
    @DisplayName("3.5 a time the engine never offered is refused")
    void refusesATimeThatWasNeverOffered() {
        Bookable bookable = newBookableBusiness("never-offered", 60);
        ZoneId zone = ZoneId.of(bookable.timezone());
        Instant offered = firstAvailableStart(bookable);

        record Attempt(String what, Instant start) {}
        java.util.List<Attempt> attempts = java.util.List.of(
                new Attempt("the middle of the night, hours outside any working window",
                        LocalTime.of(3, 0).atDate(BOOKING_DATE).atZone(zone).toInstant()),
                new Attempt("seven minutes past an offered slot, so off the step grid entirely",
                        offered.plusSeconds(7 * 60)),
                new Attempt("late enough that the service would run past closing time",
                        LocalTime.of(16, 45).atDate(BOOKING_DATE).atZone(zone).toInstant()));

        for (Attempt attempt : attempts) {
            ResponseEntity<String> response =
                    book(bookable, bookable.employeeId(), attempt.start(), UUID.randomUUID() + "@x.test");

            assertThat(response.getStatusCode().value())
                    .as("%s (%s): a booking the engine would never have offered must be refused as "
                            + "the caller's mistake, not accepted and not answered with a server error",
                            attempt.what(), attempt.start())
                    .isBetween(400, 499);
            assertThat(json(response).path("code").asText())
                    .as("%s: the refusal names a code a client can branch on", attempt.what())
                    .isNotBlank();
        }

        assertThat(appointmentCount(bookable.businessId()))
                .as("nothing was written for any of the refused times")
                .isZero();

        // The control: the time the engine *does* offer is accepted, so the refusals above were
        // about the times and not about booking being broken.
        assertThat(book(bookable, bookable.employeeId(), offered, UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("a time the engine offers is bookable")
                .isEqualTo(201);
    }

    /** 3.6 — an employee who does not perform the service cannot be booked for it. */
    @Test
    @DisplayName("3.6 an employee who does not perform the service is refused")
    void refusesAnEmployeeWhoDoesNotPerformTheService() {
        Bookable bookable = newBookableBusiness("unlinked", 60);
        Instant slot = firstAvailableStart(bookable);

        // Working the same hours, simply not qualified for this service.
        String unlinked = newEmployee(bookable.owner(), bookable.businessId(), "Unqualified " + UUID.randomUUID())
                .path("id")
                .asText();
        for (java.time.DayOfWeek weekday : java.time.DayOfWeek.values()) {
            newWorkingHours(bookable.owner(), bookable.businessId(), unlinked, weekday, "09:00:00", "17:00:00");
        }

        ResponseEntity<String> response = book(bookable, unlinked, slot, UUID.randomUUID() + "@x.test");

        assertThat(response.getStatusCode().value())
                .as("a free diary is not a qualification: booking someone for a service they do "
                        + "not perform sends a customer to a person who cannot serve them")
                .isBetween(400, 499);
        assertThat(appointmentCount(bookable.businessId())).as("nothing was written").isZero();

        // And the employee who does perform it is bookable at the same time, so the refusal was
        // about the link and not about the slot.
        assertThat(book(bookable, bookable.employeeId(), slot, UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("the qualified employee is bookable at that time")
                .isEqualTo(201);
    }

    /**
     * 3.9 — an appointment occupies {@code [start, start + duration)} as the duration stood when it
     * was booked, so editing the service afterwards does not silently move appointments already
     * made.
     *
     * <p>There is no route for changing a service's duration, so the change is made in the
     * database — which is also the harder case: if the appointment's end were derived from the
     * service at read time rather than stored at booking time, this is where it would show.
     */
    @Test
    @DisplayName("3.9 the duration is fixed at booking time, not read from the service later")
    void durationIsFixedAtBookingTime() {
        Bookable bookable = newBookableBusiness("duration", 60);
        Instant slot = firstAvailableStart(bookable);
        String appointmentId = bookOrFail(bookable, slot, UUID.randomUUID() + "@x.test").path("id").asText();

        java.util.Map<String, Object> before = jdbc().queryForMap(
                "select starts_at, ends_at from appointments where id = ?::uuid", appointmentId);
        Instant bookedStart = ((java.sql.Timestamp) before.get("starts_at")).toInstant();
        Instant bookedEnd = ((java.sql.Timestamp) before.get("ends_at")).toInstant();
        assertThat(Duration.between(bookedStart, bookedEnd))
                .as("the appointment occupies exactly the service's duration at booking time")
                .isEqualTo(Duration.ofMinutes(bookable.durationMinutes()));

        jdbc().update("update services set duration_minutes = 120 where id = ?::uuid", bookable.serviceId());

        java.util.Map<String, Object> after = jdbc().queryForMap(
                "select starts_at, ends_at from appointments where id = ?::uuid", appointmentId);
        assertThat(((java.sql.Timestamp) after.get("starts_at")).toInstant())
                .as("doubling the service must not move an appointment somebody already has")
                .isEqualTo(bookedStart);
        assertThat(((java.sql.Timestamp) after.get("ends_at")).toInstant())
                .as("nor may it quietly extend one: the customer agreed to an hour, and the person "
                        + "booked after them agreed to the hour following it")
                .isEqualTo(bookedEnd);

        // The new duration does apply to what is booked from now on.
        Instant later = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE)
                .stream()
                .filter(start -> !start.isBefore(bookedEnd))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slot left after the existing appointment"));
        String secondId = bookOrFail(bookable, later, UUID.randomUUID() + "@x.test").path("id").asText();
        java.util.Map<String, Object> second = jdbc().queryForMap(
                "select starts_at, ends_at from appointments where id = ?::uuid", secondId);
        assertThat(Duration.between(
                        ((java.sql.Timestamp) second.get("starts_at")).toInstant(),
                        ((java.sql.Timestamp) second.get("ends_at")).toInstant()))
                .as("a booking made after the change takes the new duration")
                .isEqualTo(Duration.ofMinutes(120));
    }

    // -------------------------------------------------------------------- 3.28

    /**
     * 3.28 — a booking that starts in the past is refused with 400.
     *
     * <p>The time chosen is one that <em>would</em> have been offered: the same hour on the same
     * weekday as a bookable slot, two days ago. That is what makes the assertion about the past
     * rather than about the grid, and the control at the end proves it — the identical wall-clock
     * time inside the horizon books without complaint.
     */
    @Test
    @DisplayName("3.28 a booking that starts in the past is refused with 400")
    void refusesAPastStart() {
        Bookable bookable = newBookableBusiness("past", 60);
        ZoneId zone = ZoneId.of(bookable.timezone());
        LocalDate twoDaysAgo = LocalDate.now(zone).minusDays(2);
        Instant past = LocalTime.of(10, 0).atDate(twoDaysAgo).atZone(zone).toInstant();
        assertThat(past).as("the fixture is only about the past if it is in the past").isBefore(Instant.now());

        ResponseEntity<String> response =
                book(bookable, bookable.employeeId(), past, UUID.randomUUID() + "@x.test");

        assertThat(response.getStatusCode().value())
                .as("a booking for %s cannot be honoured by anyone; accepting it puts a customer in "
                        + "the diary for a morning that has already gone", past)
                .isEqualTo(400);
        assertThat(json(response).path("code").asText())
                .as("the refusal names a code a client can branch on")
                .isNotBlank();
        assertThat(appointmentCount(bookable.businessId())).as("nothing was written").isZero();

        assertThat(book(bookable, bookable.employeeId(), firstAvailableStart(bookable),
                                UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("the same hour of the same weekday, inside the horizon, is bookable — so the "
                        + "refusal above was about the date and not about the time of day")
                .isEqualTo(201);
    }

    /**
     * 3.28 — a booking beyond the configured horizon is refused with 400.
     *
     * <p>A horizon exists so that a diary cannot be filled years ahead by anyone who feels like it,
     * and so that a business is not held to a promise made before it knew its own opening hours.
     * The boundary is asserted from both sides: just inside is accepted, well outside is not.
     */
    @Test
    @DisplayName("3.28 a booking beyond the horizon is refused with 400")
    void refusesBeyondTheHorizon() {
        Bookable bookable = newBookableBusiness("horizon", 60);
        ZoneId zone = ZoneId.of(bookable.timezone());
        LocalDate wellBeyond = LocalDate.now(zone).plusDays(HORIZON_DAYS + 30);
        Instant tooFar = LocalTime.of(10, 0).atDate(wellBeyond).atZone(zone).toInstant();

        ResponseEntity<String> response =
                book(bookable, bookable.employeeId(), tooFar, UUID.randomUUID() + "@x.test");

        assertThat(response.getStatusCode().value())
                .as("%s is %d days out against a horizon of %d; a diary open indefinitely is one "
                        + "anybody can fill", tooFar, HORIZON_DAYS + 30, HORIZON_DAYS)
                .isEqualTo(400);
        assertThat(json(response).path("code").asText()).isNotBlank();
        assertThat(appointmentCount(bookable.businessId())).as("nothing was written").isZero();

        // Just inside the horizon is a different answer, or the horizon is not where it says it is.
        LocalDate justInside = LocalDate.now(zone).plusDays(HORIZON_DAYS - 2);
        List<Instant> insideStarts = availableStarts(
                bookable.owner(), bookable.businessId(), bookable.serviceId(), bookable.employeeId(), justInside);
        assertThat(insideStarts)
                .as("a date inside the horizon is still offered by the engine")
                .isNotEmpty();
        assertThat(book(bookable, bookable.employeeId(), insideStarts.get(0), UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("%s is inside the horizon and must be bookable, or the limit is stricter than "
                        + "the configuration says", justInside)
                .isEqualTo(201);
    }
}
