package com.bookly.availability;

import com.bookly.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.bookly.availability.dto.AvailabilityDtos.AvailableSlot;
import com.bookly.business.Business;
import com.bookly.business.BusinessRepository;
import com.bookly.common.error.ApiException;
import com.bookly.employee.Employee;
import com.bookly.employee.EmployeeRepository;
import com.bookly.service.ServiceCatalog;
import com.bookly.service.ServiceOffering;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the calculator's inputs and merges its answers across employees.
 *
 * <p>All the reasoning about time lives in {@link AvailabilityCalculator}; this class only fetches.
 * That split is why the hard part is testable without a database.
 */
@Service
public class AvailabilityService {

    private final BusinessRepository businesses;
    private final EmployeeRepository employees;
    private final ServiceCatalog serviceCatalog;
    private final WorkingHoursRepository workingHours;
    private final BlockedTimeRepository blockedTimes;
    private final Duration step;

    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2100;

    public AvailabilityService(BusinessRepository businesses,
                               EmployeeRepository employees,
                               ServiceCatalog serviceCatalog,
                               WorkingHoursRepository workingHours,
                               BlockedTimeRepository blockedTimes,
                               @Value("${bookly.availability.step:PT15M}") Duration step) {
        this.businesses = businesses;
        this.employees = employees;
        this.serviceCatalog = serviceCatalog;
        this.workingHours = workingHours;
        this.blockedTimes = blockedTimes;
        this.step = step;
    }

    /**
     * Refuses a date far outside any plausible booking horizon.
     *
     * <p>{@code ISO_DATE} accepts expanded years, so {@code +999999999-12-31} parses, reaches this
     * service, and throws from {@code plusDays(1)} — past the malformed-request handlers, into the
     * catch-all, and out as 500 with a stack trace written per request. A 400 belongs there, and
     * bounding the range also bounds what a caller can ask this endpoint to compute once turn 3
     * exposes it publicly.
     */
    private static void requireSaneDate(LocalDate date) {
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw ApiException.badRequest("DATE_OUT_OF_RANGE",
                    "That date is outside the range this calendar covers.");
        }
    }

    /**
     * @param employeeId null means "any available employee" — the union across everyone who can
     *                   perform the service, deduplicated by start instant
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse availableOn(UUID businessId, UUID serviceId, UUID employeeId,
                                            LocalDate date) {
        requireSaneDate(date);
        Business business = businesses.findById(businessId)
                .orElseThrow(ApiException::noBusinessAccess);
        ZoneId zone = ZoneId.of(business.getTimezone());
        ServiceOffering offering = serviceCatalog.require(businessId, serviceId);

        List<Employee> eligible = employees.findEligibleFor(businessId, serviceId);
        if (employeeId != null) {
            eligible = eligible.stream().filter(e -> e.getId().equals(employeeId)).toList();
        }

        // The day's outer bounds in the business's own zone. Used only to narrow the blocked-time
        // query; the calculator decides what is actually inside a working window.
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        // Insertion-ordered so slots come back in the order they were found, then sorted below.
        Map<Instant, List<UUID>> byStart = new LinkedHashMap<>();
        for (Employee employee : eligible) {
            List<WorkingWindow> windows = workingHours
                    .findByBusinessIdAndEmployeeIdAndWeekdayOrderByStartTimeAsc(
                            businessId, employee.getId(), (short) date.getDayOfWeek().getValue())
                    .stream()
                    .map(WorkingHours::toWindow)
                    .toList();
            if (windows.isEmpty()) {
                continue;
            }
            List<BusyInterval> busy = blockedTimes
                    .findOverlapping(businessId, employee.getId(), dayStart, dayEnd).stream()
                    .map(BlockedTime::toBusyInterval)
                    .toList();

            for (Instant start : AvailabilityCalculator.startTimes(
                    date, zone, windows, busy, offering.getDuration(), step)) {
                byStart.computeIfAbsent(start, key -> new ArrayList<>()).add(employee.getId());
            }
        }

        List<AvailableSlot> slots = byStart.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AvailableSlot(
                        entry.getKey(),
                        entry.getKey().plus(offering.getDuration()),
                        entry.getValue().stream().sorted().toList()))
                .toList();

        return new AvailabilityResponse(
                serviceId, date.toString(), zone.getId(), step.toMinutes(), slots);
    }
}
