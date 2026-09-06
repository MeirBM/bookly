package com.bookly.appointment;

/**
 * The statuses an appointment can hold.
 *
 * <p>{@link #PENDING} and {@link #CONFIRMED} are the two that occupy time, and the exclusion
 * constraint in V5 names exactly those. Anything added here that should block a slot must be added
 * there too, in a new migration — the enum and the constraint are one decision expressed twice, and
 * {@link #occupiesTime()} exists so the Java half is stated in one place rather than scattered.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public boolean occupiesTime() {
        return this == PENDING || this == CONFIRMED;
    }
}
