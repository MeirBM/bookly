package com.bookly.appointment;

import com.bookly.appointment.dto.BookingRequests.AppointmentResponse;
import com.bookly.appointment.dto.BookingRequests.CreateBooking;
import com.bookly.appointment.dto.BookingRequests.Reschedule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Appointments")
public class AppointmentController {

    private final BookingService booking;
    private final AppointmentQueries queries;

    public AppointmentController(BookingService booking, AppointmentQueries queries) {
        this.booking = booking;
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "Appointments in a date range, for the list and the calendar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointments, possibly none"),
            @ApiResponse(responseCode = "400", description = "The range is missing or too wide"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business")})
    public List<AppointmentResponse> list(
            @PathVariable UUID businessId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queries.between(businessId, from, to);
    }

    @PostMapping
    @Operation(summary = "Book on a customer's behalf")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booked"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "409",
                    description = "The time was taken, or was never offered")})
    public ResponseEntity<AppointmentResponse> create(@PathVariable UUID businessId,
                                                      @Valid @RequestBody CreateBooking request) {
        Appointment created = booking.book(businessId, request);
        return ResponseEntity.status(201).body(queries.describe(businessId, created));
    }

    @PostMapping("/{appointmentId}/cancellation")
    @Operation(summary = "Cancel an appointment, freeing its time")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled, or already cancelled"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such appointment here")})
    public AppointmentResponse cancel(@PathVariable UUID businessId,
                                      @PathVariable UUID appointmentId) {
        return queries.describe(businessId, booking.cancel(businessId, appointmentId));
    }

    @PostMapping("/{appointmentId}/reschedule")
    @Operation(summary = "Move an appointment; it stays exactly as it was if the move fails")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Moved"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such appointment here"),
            @ApiResponse(responseCode = "409",
                    description = "The new time was taken, or was never offered")})
    public AppointmentResponse reschedule(@PathVariable UUID businessId,
                                          @PathVariable UUID appointmentId,
                                          @Valid @RequestBody Reschedule request) {
        Appointment moved = booking.reschedule(
                businessId, appointmentId, request.startsAt(), request.employeeId());
        return queries.describe(businessId, moved);
    }
}
