package com.bookly.availability.dto;

import jakarta.validation.constraints.NotNull;
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

    public record AvailabilityResponse(UUID serviceId, String date, String timezone,
                                       List<AvailableSlot> slots) {
    }

    public record CreateBlockedTime(
            UUID employeeId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            String reason) {
    }

    public record BlockedTimeResponse(UUID id, UUID employeeId, Instant startsAt, Instant endsAt,
                                      String reason) {
    }
}
