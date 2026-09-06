package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookly.support.ApiIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Turn-3 criteria 3.1 and 3.4: the guarantee itself, tested where it lives.
 *
 * <p>Every assertion here goes around the application. That is the point of the criterion — a rule
 * enforced by service code holds only for the paths that remember to ask, and this project will
 * later have a repair script, an import, and whatever turn 4 brings. A constraint cannot be
 * forgotten by a code path that did not exist when it was written.
 */
class AppointmentConstraintIT extends ApiIntegrationTest {

    private String insertCustomer(String businessId, String email) {
        return jdbc().queryForObject(
                "insert into customers (business_id, full_name, email) values (?::uuid, ?, ?) returning id::text",
                String.class,
                businessId,
                "Direct Customer",
                email);
    }

    private void insertAppointment(
            Bookable bookable, String customerId, Instant start, Instant end, String status) {
        jdbc().update(
                "insert into appointments "
                        + "(business_id, employee_id, service_id, customer_id, starts_at, ends_at, status) "
                        + "values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?)",
                bookable.businessId(),
                bookable.employeeId(),
                bookable.serviceId(),
                customerId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end),
                status);
    }

    /** 3.1 — a direct INSERT of an overlapping row fails, with no application code involved. */
    @Test
    @DisplayName("3.1 the database refuses an overlap without any application code")
    void databaseRefusesAnOverlapWithoutApplicationCode() {
        Bookable bookable = newBookableBusiness("constraint", 60);
        String customerId = insertCustomer(bookable.businessId(), "direct-" + java.util.UUID.randomUUID() + "@x.test");
        Instant ten = BOOKING_DATE.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC);

        insertAppointment(bookable, customerId, ten, ten.plusSeconds(3600), "PENDING");

        assertThatThrownBy(() ->
                        insertAppointment(bookable, customerId, ten.plusSeconds(1800), ten.plusSeconds(5400), "PENDING"))
                .as("an INSERT that overlaps an existing appointment for the same employee must be "
                        + "refused by the database, not by whoever remembered to check")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("appointments_no_overlap");

        // Containment and identity are overlaps too, not only partial ones.
        assertThatThrownBy(() ->
                        insertAppointment(bookable, customerId, ten.plusSeconds(600), ten.plusSeconds(1200), "PENDING"))
                .as("an appointment entirely inside another is still an overlap")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAppointment(bookable, customerId, ten, ten.plusSeconds(3600), "CONFIRMED"))
                .as("PENDING and CONFIRMED both occupy the time, so one cannot be laid over the other")
                .isInstanceOf(DataIntegrityViolationException.class);

        Integer rows = jdbc().queryForObject(
                "select count(*) from appointments where employee_id = ?::uuid", Integer.class, bookable.employeeId());
        assertThat(rows).as("only the first appointment was ever stored").isEqualTo(1);

        // The other direction of the same rule: a cancelled appointment must stop occupying its
        // time, or a cancelled slot could never be rebooked. Pitfall 2 names both directions.
        jdbc().update("update appointments set status = 'CANCELLED' where employee_id = ?::uuid",
                bookable.employeeId());
        assertThatCode(() -> insertAppointment(bookable, customerId, ten, ten.plusSeconds(3600), "PENDING"))
                .as("a cancelled appointment must not block its former time")
                .doesNotThrowAnyException();
    }

    /** 3.4 — two appointments may touch: the range is half-open, so 10:00-11:00 and 11:00-12:00 fit. */
    @Test
    @DisplayName("3.4 back-to-back appointments are permitted")
    void backToBackAppointmentsArePermitted() {
        Bookable bookable = newBookableBusiness("backtoback", 60);
        String customerId = insertCustomer(bookable.businessId(), "b2b-" + java.util.UUID.randomUUID() + "@x.test");
        Instant ten = BOOKING_DATE.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC);

        insertAppointment(bookable, customerId, ten, ten.plusSeconds(3600), "PENDING");

        assertThatCode(() -> insertAppointment(
                        bookable, customerId, ten.plusSeconds(3600), ten.plusSeconds(7200), "PENDING"))
                .as("an appointment beginning exactly when the previous one ends does not overlap "
                        + "it; back-to-back is the normal case for a barber, and forbidding it "
                        + "would halve the day")
                .doesNotThrowAnyException();
        assertThatCode(() -> insertAppointment(
                        bookable, customerId, ten.minusSeconds(3600), ten, "PENDING"))
                .as("and an appointment ending exactly when the next begins is equally fine")
                .doesNotThrowAnyException();

        Integer rows = jdbc().queryForObject(
                "select count(*) from appointments where employee_id = ?::uuid", Integer.class, bookable.employeeId());
        assertThat(rows).as("three touching appointments all stored").isEqualTo(3);

        // The same through the API, since that is where a customer meets it.
        Bookable viaApi = newBookableBusiness("backtoback-api", 60);
        Instant first = firstAvailableStart(viaApi);
        bookOrFail(viaApi, first, "first-" + java.util.UUID.randomUUID() + "@x.test");
        assertThat(book(viaApi, viaApi.employeeId(), first.plusSeconds(3600),
                                "second-" + java.util.UUID.randomUUID() + "@x.test")
                        .getStatusCode()
                        .value())
                .as("booking the hour immediately after an existing appointment must succeed")
                .isEqualTo(201);
    }
}
