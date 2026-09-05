package com.bookly.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response shapes for services. No businessId: it comes from the path and the guard. */
public final class ServiceRequests {

    private ServiceRequests() {
    }

    public record CreateService(
            @NotBlank @Size(max = 120) String name,
            // A zero or negative duration is refused here rather than by the database, so the
            // caller gets 400 and a field name instead of 500 and a constraint violation.
            @Min(1) @Max(1440) int durationMinutes,
            @PositiveOrZero Long priceMinor) {
    }

    public record ServiceResponse(UUID id, String name, int durationMinutes, Long priceMinor) {
    }
}
