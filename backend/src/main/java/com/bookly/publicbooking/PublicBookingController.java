package com.bookly.publicbooking;

import com.bookly.publicbooking.dto.PublicDtos.PublicAvailability;
import com.bookly.publicbooking.dto.PublicDtos.PublicBookingConfirmation;
import com.bookly.publicbooking.dto.PublicDtos.PublicBookingRequest;
import com.bookly.publicbooking.dto.PublicDtos.PublicBusiness;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer-facing surface: no account, no token.
 *
 * <p>Deliberately its own controller rather than the authenticated one with the guard removed.
 * These routes carry the project's only unauthenticated write, so everything about them — the
 * shapes they return, the limits they are held to — is decided here where it can be read in one
 * place, instead of inferred from which annotation is missing.
 */
@RestController
@RequestMapping("/api/public/businesses/{slug}")
@Tag(name = "Public booking")
public class PublicBookingController {

    private final PublicBookingService publicBooking;

    public PublicBookingController(PublicBookingService publicBooking) {
        this.publicBooking = publicBooking;
    }

    @GetMapping
    @Operation(summary = "The booking page: services and people, by slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The bookable business"),
            @ApiResponse(responseCode = "404",
                    description = "No bookable business here — identical for an unknown slug")})
    public PublicBusiness business(@PathVariable String slug) {
        return publicBooking.bookingPage(slug);
    }

    @GetMapping("/availability")
    @Operation(summary = "Free times for a service on a date; omit employeeId for any")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Free times, possibly none"),
            @ApiResponse(responseCode = "400", description = "The date is outside the range served"),
            @ApiResponse(responseCode = "404", description = "No bookable business here")})
    public PublicAvailability availability(
            @PathVariable String slug,
            @RequestParam UUID serviceId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return publicBooking.availability(slug, serviceId, employeeId, date);
    }

    @PostMapping("/appointments")
    @Operation(summary = "Book a time as a visitor, without an account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booked"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No bookable business here"),
            @ApiResponse(responseCode = "409",
                    description = "The time was taken, or was never offered"),
            @ApiResponse(responseCode = "429", description = "Too many requests")})
    public ResponseEntity<PublicBookingConfirmation> book(
            @PathVariable String slug,
            @Valid @RequestBody PublicBookingRequest request) {
        return ResponseEntity.status(201).body(publicBooking.book(slug, request));
    }
}
