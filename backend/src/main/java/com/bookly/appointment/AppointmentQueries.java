package com.bookly.appointment;

import com.bookly.appointment.dto.BookingRequests.AppointmentResponse;
import com.bookly.business.Business;
import com.bookly.business.BusinessRepository;
import com.bookly.common.error.ApiException;
import com.bookly.customer.Customer;
import com.bookly.customer.CustomerRepository;
import com.bookly.employee.Employee;
import com.bookly.employee.EmployeeRepository;
import com.bookly.service.ServiceOffering;
import com.bookly.service.ServiceOfferingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads appointments for the owner's screens.
 *
 * <p>Separate from {@link BookingService} because the questions differ: booking is about whether a
 * time may be taken, this is about presenting what was. Keeping them apart stops the read path
 * growing transactional write concerns it does not need.
 */
@Service
public class AppointmentQueries {

    /** A calendar shows a week; a range wider than this is a client bug or someone probing. */
    private static final int MAX_RANGE_DAYS = 62;

    private final AppointmentRepository appointments;
    private final BusinessRepository businesses;
    private final ServiceOfferingRepository services;
    private final EmployeeRepository employees;
    private final CustomerRepository customers;

    public AppointmentQueries(AppointmentRepository appointments,
                              BusinessRepository businesses,
                              ServiceOfferingRepository services,
                              EmployeeRepository employees,
                              CustomerRepository customers) {
        this.appointments = appointments;
        this.businesses = businesses;
        this.services = services;
        this.employees = employees;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> between(UUID businessId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "The range must end after it starts.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_WIDE",
                    "Ask for a shorter range: at most " + MAX_RANGE_DAYS + " days.");
        }
        Business business = businesses.findById(businessId)
                .orElseThrow(ApiException::noBusinessAccess);
        ZoneId zone = ZoneId.of(business.getTimezone());

        // The owner asked for calendar days in their own zone, not a span of UTC.
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Appointment> found = appointments.findForBusinessBetween(businessId, start, end);
        if (found.isEmpty()) {
            return List.of();
        }

        // Three lookups for the whole page rather than three per row: a week of appointments
        // should not cost a query each to name its service, its employee and its customer.
        Map<UUID, ServiceOffering> serviceById = services.findByBusinessIdOrderByName(businessId)
                .stream().collect(Collectors.toMap(ServiceOffering::getId, Function.identity()));
        Map<UUID, Employee> employeeById = employees.findByBusinessIdOrderByFullName(businessId)
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<UUID, Customer> customerById = customers
                .findAllById(found.stream().map(Appointment::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        return found.stream()
                .map(appointment -> toResponse(appointment,
                        serviceById.get(appointment.getServiceId()),
                        employeeById.get(appointment.getEmployeeId()),
                        customerById.get(appointment.getCustomerId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse describe(UUID businessId, Appointment appointment) {
        return toResponse(appointment,
                services.findByIdAndBusinessId(appointment.getServiceId(), businessId).orElse(null),
                employees.findByIdAndBusinessId(appointment.getEmployeeId(), businessId)
                        .orElse(null),
                customers.findByIdAndBusinessId(appointment.getCustomerId(), businessId)
                        .orElse(null));
    }

    private static AppointmentResponse toResponse(Appointment appointment, ServiceOffering service,
                                                  Employee employee, Customer customer) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getServiceId(),
                service != null ? service.getName() : null,
                appointment.getEmployeeId(),
                employee != null ? employee.getFullName() : null,
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus().name(),
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getEmail() : null,
                customer != null ? customer.getPhone() : null);
    }
}
