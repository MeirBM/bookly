package com.bookly.publicbooking;

import com.bookly.appointment.Appointment;
import com.bookly.appointment.BookingService;
import com.bookly.appointment.dto.BookingRequests.CreateBooking;
import com.bookly.availability.AvailabilityService;
import com.bookly.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.bookly.business.Business;
import com.bookly.business.BusinessRepository;
import com.bookly.common.error.ApiException;
import com.bookly.employee.Employee;
import com.bookly.employee.EmployeeRepository;
import com.bookly.publicbooking.dto.PublicDtos.PublicAvailability;
import com.bookly.publicbooking.dto.PublicDtos.PublicBookingConfirmation;
import com.bookly.publicbooking.dto.PublicDtos.PublicBookingRequest;
import com.bookly.publicbooking.dto.PublicDtos.PublicBusiness;
import com.bookly.publicbooking.dto.PublicDtos.PublicEmployee;
import com.bookly.publicbooking.dto.PublicDtos.PublicService;
import com.bookly.publicbooking.dto.PublicDtos.PublicSlot;
import com.bookly.service.ServiceOffering;
import com.bookly.service.ServiceOfferingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBookingService {

    private final BusinessRepository businesses;
    private final ServiceOfferingRepository services;
    private final EmployeeRepository employees;
    private final AvailabilityService availability;
    private final BookingService booking;

    public PublicBookingService(BusinessRepository businesses,
                                ServiceOfferingRepository services,
                                EmployeeRepository employees,
                                AvailabilityService availability,
                                BookingService booking) {
        this.businesses = businesses;
        this.services = services;
        this.employees = employees;
        this.availability = availability;
        this.booking = booking;
    }

    /**
     * @throws ApiException 404 for an unknown slug and for a business that exists but cannot be
     *         booked — criterion 3.17. Distinguishing them would let anyone enumerate which
     *         businesses are registered here.
     */
    @Transactional(readOnly = true)
    public PublicBusiness bookingPage(String slug) {
        Business business = requireBookable(slug);

        List<PublicService> offered = services.findByBusinessIdOrderByName(business.getId())
                .stream()
                .map(service -> new PublicService(service.getId(), service.getName(),
                        service.getDurationMinutes(), service.getPriceMinor()))
                .toList();

        List<PublicEmployee> people = employees
                .findByBusinessIdOrderByFullName(business.getId()).stream()
                .map(employee -> new PublicEmployee(employee.getId(), employee.getFullName()))
                .toList();

        return new PublicBusiness(business.getSlug(), business.getName(), business.getTimezone(),
                offered, people);
    }

    @Transactional(readOnly = true)
    public PublicAvailability availability(String slug, UUID serviceId, UUID employeeId,
                                           LocalDate date) {
        Business business = requireBookable(slug);
        AvailabilityResponse computed =
                availability.availableOn(business.getId(), serviceId, employeeId, date);
        return new PublicAvailability(computed.serviceId(), computed.date(), computed.timezone(),
                computed.stepMinutes(),
                computed.slots().stream()
                        .map(slot -> new PublicSlot(slot.start(), slot.end(), slot.employeeIds()))
                        .toList());
    }

    @Transactional
    public PublicBookingConfirmation book(String slug, PublicBookingRequest request) {
        Business business = requireBookable(slug);
        Appointment appointment = booking.book(business.getId(), new CreateBooking(
                request.serviceId(), request.employeeId(), request.startsAt(),
                request.customerName(), request.customerEmail(), request.customerPhone()));

        ServiceOffering service = services
                .findByIdAndBusinessId(appointment.getServiceId(), business.getId())
                .orElseThrow(() -> ApiException.notFoundInBusiness("SERVICE_NOT_FOUND",
                        "No such service."));
        Employee employee = employees
                .findByIdAndBusinessId(appointment.getEmployeeId(), business.getId())
                .orElseThrow(() -> ApiException.notFoundInBusiness("EMPLOYEE_NOT_FOUND",
                        "No such employee."));

        return new PublicBookingConfirmation(appointment.getId(), business.getName(),
                service.getName(), employee.getFullName(), appointment.getStartsAt(),
                appointment.getEndsAt(), business.getTimezone(), appointment.getStatus().name());
    }

    /** One answer for an unknown slug and an unbookable business, so neither can be probed. */
    private Business requireBookable(String slug) {
        return businesses.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundInBusiness("BUSINESS_NOT_BOOKABLE",
                        "No bookable business at that address."));
    }
}
