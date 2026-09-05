package com.bookly.business;

import com.bookly.auth.BooklyPrincipal;
import com.bookly.business.dto.BusinessResponse;
import com.bookly.business.dto.CreateBusinessRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses")
@Tag(name = "Businesses")
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping
    @Operation(summary = "Create a business; the caller becomes its owner")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or unknown timezone"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")})
    public ResponseEntity<BusinessResponse> create(
            @Valid @RequestBody CreateBusinessRequest request,
            @AuthenticationPrincipal BooklyPrincipal principal) {
        BusinessResponse created = businessService.create(request, principal.userId());
        return ResponseEntity.created(URI.create("/api/businesses/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List the businesses the caller belongs to")
    @ApiResponse(responseCode = "200", description = "The caller's businesses, possibly empty")
    public List<BusinessResponse> listMine(@AuthenticationPrincipal BooklyPrincipal principal) {
        return businessService.listForUser(principal.userId());
    }

    @GetMapping("/{businessId}")
    @Operation(summary = "Read one business the caller belongs to")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The business"),
            @ApiResponse(responseCode = "403",
                    description = "Not a member — returned identically when the business does not exist")})
    public BusinessResponse get(@PathVariable UUID businessId) {
        return businessService.get(businessId);
    }
}
