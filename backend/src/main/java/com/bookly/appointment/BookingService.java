package com.bookly.appointment;

import com.bookly.availability.AvailabilityService;
import com.bookly.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.bookly.availability.dto.AvailabilityDtos.AvailableSlot;
import com.bookly.business.Business;
import com.bookly.business.BusinessRepository;
import com.bookly.common.error.ApiException;
import com.bookly.customer.Customer;
import com.bookly.customer.CustomerRepository;
import com.bookly.employee.Employee;
import com.bookly.employee.EmployeeDirectory;
import com.bookly.service.ServiceCatalog;
import com.bookly.service.ServiceOffering;
import com.bookly.appointment.dto.BookingRequests.CreateBooking;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, cancelling and moving appointments.
 *
 * <p>Two things guard a booking, and the order matters. The availability engine is re-run
 * server-side, because the slot the browser sent arrived from a page that may be minutes old and a
 * client cannot be trusted to have current information. The exclusion constraint is the last line,
 * not the first: it is what makes a double booking impossible rather than unlikely, and it holds
 * for any future code path that forgets to ask.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** The constraint whose violation means "someone else took this slot", not "bad data". */
    private static final String OVERLAP_CONSTRAINT = "appointments_no_overlap";

    private final AppointmentRepository appointments;
    private final AppointmentStatusChangeRepository history;
    private final CustomerRepository customers;
    private final BusinessRepository businesses;
    private final ServiceCatalog serviceCatalog;
    private final EmployeeDirectory employeeDirectory;
    private final AvailabilityService availability;

    public BookingService(AppointmentRepository appointments,
                          AppointmentStatusChangeRepository history,
                          CustomerRepository customers,
                          BusinessRepository businesses,
                          ServiceCatalog serviceCatalog,
                          EmployeeDirectory employeeDirectory,
                          AvailabilityService availability) {
        this.appointments = appointments;
        this.history = history;
        this.customers = customers;
        this.businesses = businesses;
        this.serviceCatalog = serviceCatalog;
        this.employeeDirectory = employeeDirectory;
        this.availability = availability;
    }

    @Transactional
    public Appointment book(UUID businessId, CreateBooking request) {
        Business business = businesses.findById(businessId)
                .orElseThrow(ApiException::noBusinessAccess);
        ServiceOffering service = serviceCatalog.require(businessId, request.serviceId());
        Employee employee = employeeDirectory.require(businessId, request.employeeId());

        requireOffered(businessId, business, service, employee.getId(), request.startsAt());

        Customer customer = findOrCreateCustomer(businessId, request);
        Instant endsAt = request.startsAt().plus(service.getDuration());

        Appointment appointment = new Appointment(businessId, employee.getId(), service.getId(),
                customer.getId(), request.startsAt(), endsAt, AppointmentStatus.CONFIRMED);

        Appointment saved = saveOrReportTaken(appointment);
        history.save(new AppointmentStatusChange(
                saved.getId(), null, AppointmentStatus.CONFIRMED, "booked"));
        log.info("Booked appointment {} for business {} employee {}",
                saved.getId(), businessId, employee.getId());
        return saved;
    }

    @Transactional
    public Appointment cancel(UUID businessId, UUID appointmentId) {
        Appointment appointment = require(businessId, appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return appointment;
        }
        AppointmentStatus previous = appointment.getStatus();
        appointment.changeStatus(AppointmentStatus.CANCELLED);
        appointments.saveAndFlush(appointment);
        history.save(new AppointmentStatusChange(
                appointment.getId(), previous, AppointmentStatus.CANCELLED, "cancelled"));
        return appointment;
    }

    /**
     * Moves an appointment, or leaves it exactly as it was.
     *
     * <p>One transaction, and the constraint decides. Done as delete-then-insert this could free the
     * old slot, fail to take the new one, and leave the customer with nothing — criterion 3.7 is
     * written against that failure.
     */
    @Transactional
    public Appointment reschedule(UUID businessId, UUID appointmentId, Instant newStart,
                                  UUID newEmployeeId) {
        Appointment appointment = require(businessId, appointmentId);
        if (!appointment.getStatus().occupiesTime()) {
            throw ApiException.conflict("APPOINTMENT_NOT_ACTIVE",
                    "Only an active appointment can be rescheduled.");
        }
        Business business = businesses.findById(businessId)
                .orElseThrow(ApiException::noBusinessAccess);
        ServiceOffering service = serviceCatalog.require(businessId, appointment.getServiceId());
        UUID employeeId = newEmployeeId != null ? newEmployeeId : appointment.getEmployeeId();
        employeeDirectory.require(businessId, employeeId);

        requireOffered(businessId, business, service, employeeId, newStart);

        appointment.moveTo(newStart, newStart.plus(service.getDuration()));
        Appointment saved = saveOrReportTaken(appointment);
        history.save(new AppointmentStatusChange(saved.getId(), saved.getStatus(),
                saved.getStatus(), "rescheduled"));
        return saved;
    }

    @Transactional(readOnly = true)
    public Appointment require(UUID businessId, UUID appointmentId) {
        return appointments.findByIdAndBusinessId(appointmentId, businessId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("APPOINTMENT_NOT_FOUND",
                        "No such appointment in this business."));
    }

    /**
     * Re-derives availability rather than trusting the requested time.
     *
     * <p>The slot arrived from a page that may be minutes old, and nothing stops a caller sending a
     * time that was never offered — outside working hours, inside a break, or for an employee who
     * does not perform the service. The constraint would not catch any of those: they are not
     * overlaps.
     */
    private void requireOffered(UUID businessId, Business business, ServiceOffering service,
                                UUID employeeId, Instant startsAt) {
        ZoneId zone = ZoneId.of(business.getTimezone());
        LocalDate date = startsAt.atZone(zone).toLocalDate();
        AvailabilityResponse offered =
                availability.availableOn(businessId, service.getId(), employeeId, date);

        boolean matches = offered.slots().stream()
                .filter(slot -> slot.employeeIds().contains(employeeId))
                .map(AvailableSlot::start)
                .anyMatch(startsAt::equals);

        if (!matches) {
            throw ApiException.conflict("SLOT_NOT_AVAILABLE",
                    "That time is no longer available. Please choose another.");
        }
    }

    /**
     * @throws ApiException 409 when the exclusion constraint refuses the row, which means someone
     *         else took the slot between the availability check and the insert — the race this
     *         whole design exists to make safe. Distinguished by constraint name: a duplicate
     *         customer email arrives as the same exception type, and reporting that as a double
     *         booking would send a customer looking for a conflict that does not exist.
     */
    private Appointment saveOrReportTaken(Appointment appointment) {
        try {
            return appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (mentions(ex, OVERLAP_CONSTRAINT)) {
                log.info("Booking lost the race for employee {} at {}",
                        appointment.getEmployeeId(), appointment.getStartsAt());
                throw ApiException.conflict("SLOT_TAKEN",
                        "Someone just took that time. Please choose another.");
            }
            throw ex;
        }
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

    private Customer findOrCreateCustomer(UUID businessId, CreateBooking request) {
        String email = request.customerEmail().trim();
        return customers.findByBusinessIdAndEmailIgnoreCase(businessId, email)
                .map(existing -> {
                    existing.updateContactDetails(
                            request.customerName().trim(), request.customerPhone());
                    return existing;
                })
                .orElseGet(() -> customers.save(new Customer(businessId,
                        request.customerName().trim(), email, request.customerPhone())));
    }
}
