package com.bookly.service;

import com.bookly.service.dto.ServiceRequests.CreateService;
import com.bookly.service.dto.ServiceRequests.ServiceResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses/{businessId}/services")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Services")
public class ServiceController {

    private final ServiceCatalog catalog;

    public ServiceController(ServiceCatalog catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    @Operation(summary = "Add a service this business offers")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "409", description = "A service of that name exists")})
    public ResponseEntity<ServiceResponse> create(@PathVariable UUID businessId,
                                                  @Valid @RequestBody CreateService request) {
        ServiceResponse created = catalog.create(businessId, request);
        return ResponseEntity
                .created(URI.create("/api/businesses/" + businessId + "/services/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List this business's services")
    public List<ServiceResponse> list(@PathVariable UUID businessId) {
        return catalog.list(businessId);
    }

    @DeleteMapping("/{serviceId}")
    @Operation(summary = "Remove a service and its employee links")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such service here")})
    public ResponseEntity<Void> delete(@PathVariable UUID businessId,
                                       @PathVariable UUID serviceId) {
        catalog.delete(businessId, serviceId);
        return ResponseEntity.noContent().build();
    }
}
