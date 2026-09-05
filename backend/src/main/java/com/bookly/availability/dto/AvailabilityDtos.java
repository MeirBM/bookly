package com.bookly.availability.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    /**
     * @param employeeIds who can serve this slot. Present even when one employee was requested, so
     *                    the shape does not change between the two calls and a client written for
     *                    "any employee" keeps working.
     */
    public record AvailableSlot(Instant start, Instant end, List<UUID> employeeIds) {
    }

    /**
     * @param stepMinutes how far apart candidate start times are. Returned because a client
     *                    otherwise cannot know what grid it is being given, and the value is
     *                    configuration rather than a constant.
     */
    public record AvailabilityResponse(UUID serviceId, String date, String timezone,
                                       long stepMinutes, List<AvailableSlot> slots) {
    }

    public record CreateBlockedTime(
            UUID employeeId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            // Capped because this lands in a TEXT column and is read back on every dashboard
            // load. It was the one string in this turn without a bound.
            @Size(max = 500) String reason) {
    }

    public record BlockedTimeResponse(UUID id, UUID employeeId, Instant startsAt, Instant endsAt,
                                      String reason) {
    }
}
