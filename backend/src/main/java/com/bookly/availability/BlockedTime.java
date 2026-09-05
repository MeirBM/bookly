package com.bookly.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A span of real time that is not available: a holiday, a vacation, an afternoon off.
 *
 * <p>Absolute instants rather than local times, unlike working hours — a vacation is a stretch of
 * real time, not a recurring clock position. A row with no employee applies to the whole business,
 * which is how a public holiday is expressed.
 */
@Entity
@Table(name = "blocked_times")
public class BlockedTime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** Null means the whole business is blocked. */
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column
    private String reason;

    protected BlockedTime() {
        // for JPA
    }

    public BlockedTime(UUID businessId, UUID employeeId, Instant startsAt, Instant endsAt,
                       String reason) {
        this.businessId = businessId;
        this.employeeId = employeeId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getReason() {
        return reason;
    }

    public BusyInterval toBusyInterval() {
        return new BusyInterval(startsAt, endsAt);
    }
}
