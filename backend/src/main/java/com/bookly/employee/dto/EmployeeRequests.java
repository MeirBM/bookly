package com.bookly.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EmployeeRequests {

    private EmployeeRequests() {
    }

    public record CreateEmployee(@NotBlank @Size(max = 120) String fullName) {
    }

    public record EmployeeResponse(UUID id, String fullName, List<UUID> serviceIds) {
    }

    /** Replaces the whole set, so the caller never has to reason about add-versus-remove. */
    public record SetServices(@NotNull Set<UUID> serviceIds) {
    }

    /**
     * @param weekday ISO-8601 day name, e.g. {@code MONDAY} — spelled out rather than numbered so a
     *                caller cannot silently disagree about whether the week starts on Sunday
     */
    public record CreateWorkingHours(
            @NotNull java.time.DayOfWeek weekday,
            @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt) {
    }

    public record WorkingHoursResponse(UUID id, java.time.DayOfWeek weekday,
                                       LocalTime startsAt, LocalTime endsAt) {
    }
}
