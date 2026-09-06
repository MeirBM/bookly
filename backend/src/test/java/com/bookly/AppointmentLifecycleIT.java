package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Turn-3 criteria 3.3, 3.7 and 3.8: what happens to an appointment after it exists. */
class AppointmentLifecycleIT extends ApiIntegrationTest {

    private ResponseEntity<String> cancel(Bookable bookable, String appointmentId) {
        return post(
                businessPath(bookable.businessId(), "/appointments/" + appointmentId + "/cancellation"),
                null,
                bookable.owner().accessToken());
    }

    private ResponseEntity<String> reschedule(Bookable bookable, String appointmentId, Instant to) {
        return post(
                businessPath(bookable.businessId(), "/appointments/" + appointmentId + "/reschedule"),
                body("startsAt", to.toString()),
                bookable.owner().accessToken());
    }

    private Map<String, Object> storedAppointment(String appointmentId) {
        return jdbc().queryForMap(
                "select employee_id::text, starts_at, ends_at, status from appointments where id = ?::uuid",
                appointmentId);
    }

    private List<Map<String, Object>> history(String appointmentId) {
        return jdbc().queryForList(
                "select from_status, to_status from appointment_status_history "
                        + "where appointment_id = ?::uuid order by changed_at, id",
                appointmentId);
    }

    /** 3.3 — a cancelled appointment stops occupying its time, and the slot can be taken again. */
    @Test
    @DisplayName("3.3 cancelling frees the slot for someone else")
    void cancellingFreesTheSlot() {
        Bookable bookable = newBookableBusiness("cancel", 60);
        Instant slot = firstAvailableStart(bookable);

        String appointmentId = bookOrFail(bookable, slot, "first-" + UUID.randomUUID() + "@x.test")
                .path("id")
                .asText();
        assertThat(availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE))
                .as("while the appointment stands, its time is not on offer")
                .doesNotContain(slot);

        assertThat(cancel(bookable, appointmentId).getStatusCode().is2xxSuccessful())
                .as("POST .../cancellation")
                .isTrue();

        assertThat(availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE))
                .as("a cancelled appointment must stop blocking its former time — otherwise every "
                        + "cancellation quietly removes capacity for ever")
                .contains(slot);

        assertThat(book(bookable, bookable.employeeId(), slot, "second-" + UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("and the freed time can actually be taken, not merely offered")
                .isEqualTo(201);

        assertThat(String.valueOf(storedAppointment(appointmentId).get("status")))
                .as("the cancelled appointment is kept, not deleted: the record of what happened "
                        + "outlives the booking")
                .isEqualTo("CANCELLED");
    }

    /**
     * 3.7 — a reschedule that fails leaves the original exactly as it was.
     *
     * <p>Pitfall 5: done as delete-then-insert this frees the old time, fails to take the new one,
     * and leaves the customer with nothing — an appointment that existed a moment ago and now does
     * not, with nobody informed.
     */
    @Test
    @DisplayName("3.7 a failed reschedule changes nothing")
    void aFailedRescheduleChangesNothing() {
        Bookable bookable = newBookableBusiness("reschedule", 60);
        Instant first = firstAvailableStart(bookable);
        String movingId = bookOrFail(bookable, first, "moving-" + UUID.randomUUID() + "@x.test")
                .path("id")
                .asText();

        Instant occupied = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE)
                .stream()
                .filter(start -> !start.isBefore(first.plusSeconds(3600)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no later slot to occupy"));
        String blockingId = bookOrFail(bookable, occupied, "blocking-" + UUID.randomUUID() + "@x.test")
                .path("id")
                .asText();

        Map<String, Object> before = storedAppointment(movingId);
        Map<String, Object> blockerBefore = storedAppointment(blockingId);
        int historyBefore = history(movingId).size();

        ResponseEntity<String> refused = reschedule(bookable, movingId, occupied);

        assertThat(refused.getStatusCode().value())
                .as("moving an appointment onto an occupied time must be refused, and refused as a "
                        + "conflict rather than a server fault")
                .isEqualTo(409);
        assertThat(storedAppointment(movingId))
                .as("the appointment that could not move must be byte-identical to what it was: "
                        + "same employee, same start, same end, same status")
                .isEqualTo(before);
        assertThat(storedAppointment(blockingId))
                .as("and the appointment that was in the way is untouched too")
                .isEqualTo(blockerBefore);
        assertThat(history(movingId))
                .as("a change that did not happen must not be recorded as one")
                .hasSize(historyBefore);

        Integer rows = jdbc().queryForObject(
                "select count(*) from appointments where business_id = ?::uuid", Integer.class, bookable.businessId());
        assertThat(rows).as("no appointment was created or lost by the failed move").isEqualTo(2);

        // The control: a reschedule to a free time does work, so the refusal above was about the
        // conflict and not about rescheduling being broken outright.
        Instant free = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE)
                .stream()
                .filter(start -> start.isAfter(occupied))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no free slot to move into"));
        assertThat(reschedule(bookable, movingId, free).getStatusCode().is2xxSuccessful())
                .as("rescheduling into a free time succeeds")
                .isTrue();
        assertThat(((java.sql.Timestamp) storedAppointment(movingId).get("starts_at")).toInstant())
                .as("and the appointment actually moved")
                .isEqualTo(free);
    }

    /** 3.8 — every status change writes exactly one row, including the one that creates it. */
    @Test
    @DisplayName("3.8 every status change is recorded, including creation")
    void everyStatusChangeIsRecorded() {
        Bookable bookable = newBookableBusiness("history", 60);
        Instant slot = firstAvailableStart(bookable);

        JsonNode created = bookOrFail(bookable, slot, "history-" + UUID.randomUUID() + "@x.test");
        String appointmentId = created.path("id").asText();

        List<Map<String, Object>> afterBooking = history(appointmentId);
        assertThat(afterBooking)
                .as("creation is a status change: without a row for it the trail starts in the "
                        + "middle and cannot say when the appointment came into being")
                .hasSize(1);
        assertThat(afterBooking.get(0).get("from_status"))
                .as("nothing preceded the booking, so it came from no status")
                .isNull();
        String initial = String.valueOf(afterBooking.get(0).get("to_status"));
        assertThat(initial).as("the status the appointment was created in").isIn("PENDING", "CONFIRMED");
        assertThat(String.valueOf(storedAppointment(appointmentId).get("status")))
                .as("and the history agrees with the appointment itself")
                .isEqualTo(initial);

        assertThat(cancel(bookable, appointmentId).getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> afterCancelling = history(appointmentId);
        assertThat(afterCancelling).as("one row per change, so cancelling adds exactly one").hasSize(2);
        assertThat(String.valueOf(afterCancelling.get(1).get("from_status")))
                .as("the second row says what it changed from")
                .isEqualTo(initial);
        assertThat(String.valueOf(afterCancelling.get(1).get("to_status"))).isEqualTo("CANCELLED");
    }
}
