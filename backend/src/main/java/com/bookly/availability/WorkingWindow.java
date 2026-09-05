package com.bookly.availability;

import java.time.LocalTime;

/**
 * One stretch of a weekday an employee works, in the business's local wall-clock time.
 *
 * <p>Local rather than absolute on purpose: "Tuesday 09:00" is what the owner means, and it stays
 * true across a daylight-saving change. Two windows on one weekday express a break.
 */
public record WorkingWindow(LocalTime start, LocalTime end) {

    public WorkingWindow {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("A working window must end after it starts");
        }
    }
}
