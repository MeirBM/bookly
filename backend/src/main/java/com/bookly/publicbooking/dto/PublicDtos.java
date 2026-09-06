package com.bookly.publicbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything an anonymous visitor is allowed to see.
 *
 * <p>Separate shapes from the authenticated API on purpose. Reusing the dashboard's records with
 * some fields left null is how a customer's phone number reaches a public response: the omission
 * would live in whichever mapper someone remembered to write. Here the fields do not exist, so the
 * compiler enforces it and no reviewer has to.
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    public record PublicService(UUID id, String name, int durationMinutes, Long priceMinor) {
    }

    /**
     * Name, id, and what this person performs.
     *
     * <p>No more than that: a visitor needs to choose a person, not to learn about them. The
     * service ids are already derivable by asking for availability service by service, so listing
     * them discloses nothing new and saves the page a call per service.
     */
    public record PublicEmployee(UUID id, String name, List<UUID> serviceIds) {
    }

    public record PublicBusiness(String slug, String name, String timezone,
                                 List<PublicService> services, List<PublicEmployee> employees) {
    }

    /**
     * A free time, and who could serve it.
     *
     * <p>Carries no indication of what is *not* free: a visitor learns that 10:00 is available, not
     * that 10:30 is taken or by whom.
     */
    public record PublicSlot(Instant start, Instant end, List<UUID> employeeIds) {
    }

    public record PublicAvailability(UUID serviceId, String date, String timezone,
                                     long stepMinutes, List<PublicSlot> slots) {
    }

    public record PublicBookingRequest(
            @NotNull UUID serviceId,
            @NotNull UUID employeeId,
            @NotNull Instant startsAt,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @Size(max = 40) String customerPhone) {
    }

    /** What the visitor is told back: their own booking, and nothing about anyone else's. */
    public record PublicBookingConfirmation(UUID id, String businessName, String serviceName,
                                            String employeeName, Instant startsAt, Instant endsAt,
                                            String timezone, String status) {
    }
}
