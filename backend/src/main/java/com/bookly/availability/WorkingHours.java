package com.bookly.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One stretch of one weekday that an employee works.
 *
 * <p>Two rows for the same weekday express a break, which is why there is no breaks table.
 */
@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /**
     * ISO-8601: Monday = 1 … Sunday = 7, matching {@link DayOfWeek#getValue()}.
     *
     * <p>The {@code _local} suffix on the columns below is this project's convention for a
     * wall-clock time in the business's own zone, as opposed to {@code _at} for an instant. Both
     * halves are asserted by {@code SchemaConventionsIT}.
     */
    @Column(nullable = false)
    private short weekday;

    @Column(name = "start_local", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_local", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {
        // for JPA
    }

    public WorkingHours(UUID businessId, UUID employeeId, DayOfWeek weekday,
                        LocalTime startTime, LocalTime endTime) {
        this.businessId = businessId;
        this.employeeId = employeeId;
        this.weekday = (short) weekday.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public DayOfWeek getWeekday() {
        return DayOfWeek.of(weekday);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public WorkingWindow toWindow() {
        return new WorkingWindow(startTime, endTime);
    }
}
