package com.bookly.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class BookingRequests {

    private BookingRequests() {
    }

    /**
     * What a visitor sends to book.
     *
     * <p>No {@code endsAt}: the duration comes from the service on the server. A client that could
     * choose its own end time could book a five-minute haircut into a five-minute gap.
     */
    public record CreateBooking(
            @NotNull UUID serviceId,
            @NotNull UUID employeeId,
            @NotNull Instant startsAt,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @Size(max = 40) String customerPhone) {
    }

    public record Reschedule(@NotNull Instant startsAt, UUID employeeId) {
    }

    /** What the owner sees. Carries customer details, because the owner is entitled to them. */
    public record AppointmentResponse(
            UUID id, UUID serviceId, String serviceName, UUID employeeId, String employeeName,
            Instant startsAt, Instant endsAt, String status,
            String customerName, String customerEmail, String customerPhone) {
    }

    /**
     * What a visitor sees after booking.
     *
     * <p>A deliberately different shape from {@link AppointmentResponse}, not the same record with
     * fields left null — sharing one shape between an owner's dashboard and an anonymous visitor is
     * how a customer's phone number ends up in a public body. The compiler enforces the difference
     * rather than a reviewer.
     */
    public record BookingConfirmation(
            UUID id, String serviceName, String employeeName,
            Instant startsAt, Instant endsAt, String timezone, String status) {
    }
}
