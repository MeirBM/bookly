package com.bookly.appointment;

import com.bookly.availability.AvailabilityService;
import com.bookly.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.bookly.availability.dto.AvailabilityDtos.AvailableSlot;
import com.bookly.business.Business;
import com.bookly.business.BusinessRepository;
import com.bookly.common.error.ApiException;
import com.bookly.customer.CustomerRegistry;
import com.bookly.employee.Employee;
import com.bookly.employee.EmployeeDirectory;
import com.bookly.service.ServiceCatalog;
import com.bookly.service.ServiceOffering;
import com.bookly.appointment.dto.BookingRequests.CreateBooking;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final AppointmentRepository appointments;
    private final AppointmentStatusChangeRepository history;
    private final AppointmentWriter writer;
    private final CustomerRegistry customers;
    private final Clock clock;
    private final int horizonDays;
    private final BusinessRepository businesses;
    private final ServiceCatalog serviceCatalog;
    private final EmployeeDirectory employeeDirectory;
    private final AvailabilityService availability;

    public BookingService(AppointmentRepository appointments,
                          AppointmentStatusChangeRepository history,
                          AppointmentWriter writer,
                          CustomerRegistry customers,
                          BusinessRepository businesses,
                          ServiceCatalog serviceCatalog,
                          EmployeeDirectory employeeDirectory,
                          AvailabilityService availability,
                          Clock clock,
                          @Value("${bookly.booking.horizon-days:120}") int horizonDays) {
        this.appointments = appointments;
        this.history = history;
        this.writer = writer;
        this.customers = customers;
        this.businesses = businesses;
        this.serviceCatalog = serviceCatalog;
        this.employeeDirectory = employeeDirectory;
        this.availability = availability;
        this.clock = clock;
        this.horizonDays = horizonDays;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>The customer must be found or created in its own transaction, and that transaction has to
     * complete before the appointment's begins. Nested, each booking held two pooled connections
     * and twenty concurrent requests deadlocked a pool of ten until the connection timeout. The
     * appointment and its first audit row still share one transaction, in {@link AppointmentWriter}.
     */
    public Appointment book(UUID businessId, CreateBooking request) {
        Business business = businesses.findById(businessId)
                .orElseThrow(ApiException::noBusinessAccess);
        ServiceOffering service = serviceCatalog.require(businessId, request.serviceId());
        Employee employee = employeeDirectory.require(businessId, request.employeeId());

        requireWithinHorizon(request.startsAt());
        requireOffered(businessId, business, service, employee.getId(), request.startsAt(), null);

        UUID customerId = findOrCreateCustomer(businessId, request);
        Instant endsAt = request.startsAt().plus(service.getDuration());

        Appointment appointment = new Appointment(businessId, employee.getId(), service.getId(),
                customerId, request.startsAt(), endsAt, AppointmentStatus.CONFIRMED);

        Appointment saved = writer.create(appointment);
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

        requireWithinHorizon(newStart);
        // Ignoring this appointment's own interval. Counting it as busy meant moving something
        // by less than its own duration was always refused, and reported as SLOT_TAKEN - the
        // owner was told an invisible customer held a time only they themselves occupied.
        requireOffered(businessId, business, service, employeeId, newStart, appointment.getId());

        return writer.move(appointment.getId(), newStart, newStart.plus(service.getDuration()));
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
                                UUID employeeId, Instant startsAt, UUID ignoreAppointmentId) {
        ZoneId zone = ZoneId.of(business.getTimezone());
        LocalDate date = startsAt.atZone(zone).toLocalDate();
        AvailabilityResponse offered = availability.availableOn(
                businessId, service.getId(), employeeId, date, ignoreAppointmentId);

        boolean matches = offered.slots().stream()
                .filter(slot -> slot.employeeIds().contains(employeeId))
                .map(AvailableSlot::start)
                .anyMatch(startsAt::equals);

        if (!matches) {
            // Two different situations reach here and a client must be able to tell them apart:
            // a time that was never on offer (outside working hours, an employee who does not
            // perform this service) and one that was on offer and has since been taken. Losing a
            // race is the second, and it is the case the booking page has to recover from.
            // "Taken" means: this time would have been offered, and a booking is why it was
            // not. Asking instead whether anyone is busy at the requested instant - which is what
            // the first two versions did - turns the pair of codes into a diary. An anonymous
            // caller could name a service the employee performs, probe 03:00, watch the code flip
            // between SLOT_TAKEN and SLOT_NOT_AVAILABLE, and read out private appointments hour by
            // hour, on days off included. Availability never discloses those, so neither may this.
            boolean taken = availability.wouldOfferIgnoringBookings(
                    businessId, service.getId(), employeeId, date, startsAt);
            if (taken) {
                throw ApiException.conflict("SLOT_TAKEN",
                        "Someone just took that time. Please choose another.");
            }
            throw ApiException.conflict("SLOT_NOT_AVAILABLE",
                    "That time is not available. Please choose another.");
        }
    }

    /**
     * Finds or creates the customer, retrying once if another request created it first.
     *
     * <p>Each attempt is its own transaction — {@code CustomerRegistry} is {@code REQUIRES_NEW} and
     * this method is not transactional — so the retry starts clean. That matters: two visitors
     * sharing an email address and booking different free times at the same moment used to produce
     * a 500, first because the duplicate was never caught and then because the recovery ran inside
     * a transaction the database had already aborted.
     */
    private UUID findOrCreateCustomer(UUID businessId, CreateBooking request) {
        try {
            return customers.findOrCreate(businessId, request.customerName().trim(),
                    request.customerEmail(), request.customerPhone());
        } catch (DataIntegrityViolationException ex) {
            return customers.findOrCreate(businessId, request.customerName().trim(),
                    request.customerEmail(), request.customerPhone());
        }
    }

    /**
     * Refuses a booking outside the window a business plausibly takes.
     *
     * <p>Nothing bounded this before, in either direction: an anonymous caller could write an
     * appointment into 2019 or 2099. A past booking has no honest caller at all, and an unbounded
     * future is what makes booking out every slot of every year a matter of patience.
     */
    private void requireWithinHorizon(Instant startsAt) {
        Instant now = clock.instant();
        if (startsAt.isBefore(now)) {
            throw ApiException.badRequest("START_IN_THE_PAST", "That time has already passed.");
        }
        if (startsAt.isAfter(now.plus(Duration.ofDays(horizonDays)))) {
            throw ApiException.badRequest("BEYOND_BOOKING_HORIZON",
                    "That date is too far ahead to book.");
        }
    }
}
