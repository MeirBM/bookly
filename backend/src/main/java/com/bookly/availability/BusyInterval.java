package com.bookly.availability;

import java.time.Instant;

/**
 * A span of real time an employee is not available.
 *
 * <p>Deliberately says nothing about *why*. In this turn every busy interval is a blocked time; in
 * turn 3 appointments join the same list without the calculator changing. A calculator that knew
 * the difference would have to be reopened for every future source of unavailability.
 *
 * <p>Half-open: {@code [start, end)}. A slot may begin exactly when a busy interval ends.
 */
public record BusyInterval(Instant start, Instant end) {

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return otherStart.isBefore(end) && otherEnd.isAfter(start);
    }
}
