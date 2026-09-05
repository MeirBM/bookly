package com.bookly.employee;

import com.bookly.employee.dto.EmployeeRequests.CreateEmployee;
import com.bookly.employee.dto.EmployeeRequests.CreateWorkingHours;
import com.bookly.employee.dto.EmployeeRequests.EmployeeResponse;
import com.bookly.employee.dto.EmployeeRequests.SetServices;
import com.bookly.employee.dto.EmployeeRequests.WorkingHoursResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/businesses/{businessId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeDirectory directory;

    public EmployeeController(EmployeeDirectory directory) {
        this.directory = directory;
    }

    @PostMapping("/employees")
    @Operation(summary = "Add an employee")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business")})
    public ResponseEntity<EmployeeResponse> create(@PathVariable UUID businessId,
                                                   @Valid @RequestBody CreateEmployee request) {
        EmployeeResponse created = directory.create(businessId, request);
        return ResponseEntity
                .created(URI.create("/api/businesses/" + businessId + "/employees/" + created.id()))
                .body(created);
    }

    @GetMapping("/employees")
    @Operation(summary = "List employees")
    public List<EmployeeResponse> list(@PathVariable UUID businessId) {
        return directory.list(businessId);
    }

    @DeleteMapping("/employees/{employeeId}")
    @Operation(summary = "Remove an employee")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such employee here")})
    public ResponseEntity<Void> delete(@PathVariable UUID businessId,
                                       @PathVariable UUID employeeId) {
        directory.delete(businessId, employeeId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/employees/{employeeId}/services")
    @Operation(summary = "Set which services this employee performs")
    public EmployeeResponse setServices(@PathVariable UUID businessId,
                                        @PathVariable UUID employeeId,
                                        @Valid @RequestBody SetServices request) {
        return directory.setServices(businessId, employeeId, request);
    }

    @PostMapping("/employees/{employeeId}/working-hours")
    @Operation(summary = "Add a working window; two on one weekday express a break")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "The window does not end after it starts"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such employee here"),
            @ApiResponse(responseCode = "409",
                    description = "An identical window exists, or the employee's limit is reached")})
    public ResponseEntity<WorkingHoursResponse> addWorkingHours(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody CreateWorkingHours request) {
        return ResponseEntity.status(201)
                .body(directory.addWorkingHours(businessId, employeeId, request));
    }

    @GetMapping("/employees/{employeeId}/working-hours")
    @Operation(summary = "List an employee's working windows")
    public List<WorkingHoursResponse> listWorkingHours(@PathVariable UUID businessId,
                                                       @PathVariable UUID employeeId) {
        return directory.listWorkingHours(businessId, employeeId);
    }

    @DeleteMapping("/working-hours/{workingHoursId}")
    @Operation(summary = "Remove a working window")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "403", description = "Not a member of this business"),
            @ApiResponse(responseCode = "404", description = "No such window here")})
    public ResponseEntity<Void> deleteWorkingHours(@PathVariable UUID businessId,
                                                   @PathVariable UUID workingHoursId) {
        directory.deleteWorkingHours(businessId, workingHoursId);
        return ResponseEntity.noContent().build();
    }
}
