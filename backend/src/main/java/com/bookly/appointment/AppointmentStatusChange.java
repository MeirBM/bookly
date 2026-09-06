package com.bookly.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the audit trail.
 *
 * <p>Written on creation as well as on every later change, so a dispute about what happened has an
 * answer that does not depend on anyone's memory.
 */
@Entity
@Table(name = "appointment_status_history")
public class AppointmentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    /** Null on creation: there was no previous status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AppointmentStatus toStatus;

    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;

    @Column
    private String note;

    protected AppointmentStatusChange() {
        // for JPA
    }

    public AppointmentStatusChange(UUID appointmentId, AppointmentStatus fromStatus,
                                   AppointmentStatus toStatus, String note) {
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public AppointmentStatus getFromStatus() {
        return fromStatus;
    }

    public AppointmentStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
