package com.bookly.appointment;

import com.bookly.common.error.ApiException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an appointment and its first audit row together, in one transaction.
 *
 * <p>Separate from {@link BookingService} so that the customer lookup can happen in its own
 * transaction *before* this one opens rather than nested inside it. Nesting cost the booking path
 * two pooled connections per request: twenty concurrent bookings against Hikari's default pool of
 * ten deadlocked until the connection timeout, and every one of them failed. Sequential tests could
 * not see it, and the concurrency test found it in thirty seconds.
 *
 * <p>The two writes stay together because criterion 3.8 asks for a history row on every status
 * change including creation, and an appointment with no history is a record with no provenance.
 */
@Component
public class AppointmentWriter {

    private static final Logger log = LoggerFactory.getLogger(AppointmentWriter.class);

    /** The constraint whose violation means "someone else took this slot", not "bad data". */
    private static final String OVERLAP_CONSTRAINT = "appointments_no_overlap";

    private final AppointmentRepository appointments;
    private final AppointmentStatusChangeRepository history;

    public AppointmentWriter(AppointmentRepository appointments,
                             AppointmentStatusChangeRepository history) {
        this.appointments = appointments;
        this.history = history;
    }

    @Transactional
    public Appointment create(Appointment appointment) {
        Appointment saved = saveOrReportTaken(appointment);
        history.save(new AppointmentStatusChange(
                saved.getId(), null, saved.getStatus(), "booked"));
        return saved;
    }

    @Transactional
    public Appointment move(UUID appointmentId, java.time.Instant newStart,
                            java.time.Instant newEnd) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("APPOINTMENT_NOT_FOUND",
                        "No such appointment."));
        appointment.moveTo(newStart, newEnd);
        Appointment saved = saveOrReportTaken(appointment);
        history.save(new AppointmentStatusChange(
                saved.getId(), saved.getStatus(), saved.getStatus(), "rescheduled"));
        return saved;
    }

    /**
     * @throws ApiException 409 when the exclusion constraint refuses the row, which means someone
     *         else took the slot between the availability check and the insert — the race this
     *         design exists to make safe. Distinguished by constraint name: a duplicate customer
     *         email arrives as the same exception type, and reporting that as a double booking
     *         would send someone looking for a conflict that does not exist.
     */
    private Appointment saveOrReportTaken(Appointment appointment) {
        try {
            return appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (mentions(ex, OVERLAP_CONSTRAINT)) {
                throw taken(appointment);
            }
            throw ex;
        } catch (CannotAcquireLockException ex) {
            // A deadlock on this insert means what an exclusion violation means: the row was
            // contended. Two simultaneous inserts for the same employee and instant routinely
            // deadlock on the constraint's gist index, and PostgreSQL kills one of them - which
            // arrives as CannotAcquireLockException, not DataIntegrityViolationException, so it
            // escaped the catch above and reached the caller as a 500.
            //
            // The guarantee was never in doubt: exactly one appointment existed every time. What
            // failed was the contract on top of it - nineteen people told the server had broken
            // when the truth was that someone got there first. Mapping it to the same 409 is
            // reliable in a way more retries would not be: retries lower the frequency, they do
            // not close it.
            throw taken(appointment);
        }
    }

    private ApiException taken(Appointment appointment) {
        log.info("Booking lost the race for employee {} at {}",
                appointment.getEmployeeId(), appointment.getStartsAt());
        return ApiException.conflict("SLOT_TAKEN",
                "Someone just took that time. Please choose another.");
    }

    private static boolean mentions(Throwable error, String needle) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(needle)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
