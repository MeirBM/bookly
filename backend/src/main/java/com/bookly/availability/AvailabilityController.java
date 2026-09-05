package com.bookly.availability;

import com.bookly.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.bookly.availability.dto.AvailabilityDtos.BlockedTimeResponse;
import com.bookly.availability.dto.AvailabilityDtos.CreateBlockedTime;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses/{businessId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Availability")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final BlockedTimeService blockedTimes;

    public AvailabilityController(AvailabilityService availability,
                                  BlockedTimeService blockedTimes) {
        this.availability = availability;
        this.blockedTimes = blockedTimes;
    }

    /**
     * Authenticated in this turn, like every other route here. The public equivalent the booking
     * page needs is turn 3's, and it will be a separate route with its own rate limit — a public
     * endpoint has different exposure from an owner's dashboard.
     */
    @GetMapping("/availability")
    @Operation(summary = "Free start times for a service on a date; omit employeeId for any")
    public AvailabilityResponse availability(
            @PathVariable UUID businessId,
            @RequestParam UUID serviceId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.availableOn(businessId, serviceId, employeeId, date);
    }

    @PostMapping("/blocked-times")
    @Operation(summary = "Block a period; omit employeeId to block the whole business")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "The period does not end after it starts"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business")})
    public ResponseEntity<BlockedTimeResponse> block(@PathVariable UUID businessId,
                                                     @Valid @RequestBody CreateBlockedTime request) {
        return ResponseEntity.status(201).body(blockedTimes.create(businessId, request));
    }

    @GetMapping("/blocked-times")
    @Operation(summary = "List blocked periods")
    public List<BlockedTimeResponse> listBlocked(@PathVariable UUID businessId) {
        return blockedTimes.list(businessId);
    }

    @DeleteMapping("/blocked-times/{blockedTimeId}")
    @Operation(summary = "Remove a blocked period")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such blocked period here")})
    public ResponseEntity<Void> unblock(@PathVariable UUID businessId,
                                        @PathVariable UUID blockedTimeId) {
        blockedTimes.delete(businessId, blockedTimeId);
        return ResponseEntity.noContent().build();
    }
}
