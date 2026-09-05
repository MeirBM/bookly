package com.bookly.employee;

import com.bookly.availability.WorkingHours;
import com.bookly.availability.WorkingHoursRepository;
import com.bookly.common.error.ApiException;
import com.bookly.employee.dto.EmployeeRequests.CreateEmployee;
import com.bookly.employee.dto.EmployeeRequests.CreateWorkingHours;
import com.bookly.employee.dto.EmployeeRequests.EmployeeResponse;
import com.bookly.employee.dto.EmployeeRequests.SetServices;
import com.bookly.employee.dto.EmployeeRequests.WorkingHoursResponse;
import com.bookly.service.ServiceOffering;
import com.bookly.service.ServiceOfferingRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeDirectory {

    private final EmployeeRepository employees;
    private final ServiceOfferingRepository services;
    private final WorkingHoursRepository workingHours;

    private final int maxEmployees;
    private final int maxWindowsPerEmployee;

    public EmployeeDirectory(EmployeeRepository employees,
                             ServiceOfferingRepository services,
                             WorkingHoursRepository workingHours,
                             @Value("${bookly.limits.employees-per-business:200}") int maxEmployees,
                             @Value("${bookly.limits.working-hours-per-employee:100}")
                             int maxWindowsPerEmployee) {
        this.employees = employees;
        this.services = services;
        this.workingHours = workingHours;
        this.maxEmployees = maxEmployees;
        this.maxWindowsPerEmployee = maxWindowsPerEmployee;
    }

    @Transactional
    public EmployeeResponse create(UUID businessId, CreateEmployee request) {
        if (employees.countByBusinessId(businessId) >= maxEmployees) {
            throw ApiException.limitReached("EMPLOYEE_LIMIT_REACHED",
                    "This business has reached its limit of employees.");
        }
        Employee saved = employees.save(new Employee(businessId, request.fullName().trim()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(UUID businessId) {
        return employees.findByBusinessIdOrderByFullName(businessId).stream()
                .map(EmployeeDirectory::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Employee require(UUID businessId, UUID employeeId) {
        return employees.findByIdAndBusinessId(employeeId, businessId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("EMPLOYEE_NOT_FOUND",
                        "No such employee in this business."));
    }

    @Transactional
    public void delete(UUID businessId, UUID employeeId) {
        employees.delete(require(businessId, employeeId));
    }

    /**
     * Links an employee to the services they perform.
     *
     * <p>Each service is loaded with both ids, so a caller cannot attach one business's employee to
     * another business's service by guessing an id — the tenant guard would not catch that, because
     * the business in the path is genuinely theirs.
     */
    @Transactional
    public EmployeeResponse setServices(UUID businessId, UUID employeeId, SetServices request) {
        Employee employee = require(businessId, employeeId);
        Set<ServiceOffering> replacement = new HashSet<>();
        for (UUID serviceId : request.serviceIds()) {
            replacement.add(services.findByIdAndBusinessId(serviceId, businessId)
                    .orElseThrow(() -> ApiException.notFoundInBusiness("SERVICE_NOT_FOUND",
                            "No such service in this business.")));
        }
        employee.replaceServices(replacement);
        return toResponse(employee);
    }

    @Transactional
    public WorkingHoursResponse addWorkingHours(UUID businessId, UUID employeeId,
                                                CreateWorkingHours request) {
        require(businessId, employeeId);
        if (workingHours.countByBusinessIdAndEmployeeId(businessId, employeeId)
                >= maxWindowsPerEmployee) {
            throw ApiException.limitReached("WORKING_HOURS_LIMIT_REACHED",
                    "This employee has reached its limit of working windows.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("INVALID_WORKING_WINDOW",
                    "A working window must end after it starts.");
        }
        WorkingHours saved = workingHours.save(new WorkingHours(
                businessId, employeeId, request.weekday(), request.startsAt(), request.endsAt()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> listWorkingHours(UUID businessId, UUID employeeId) {
        require(businessId, employeeId);
        return workingHours
                .findByBusinessIdAndEmployeeIdOrderByWeekdayAscStartTimeAsc(businessId, employeeId)
                .stream()
                .map(EmployeeDirectory::toResponse)
                .toList();
    }

    @Transactional
    public void deleteWorkingHours(UUID businessId, UUID workingHoursId) {
        workingHours.delete(workingHours.findByIdAndBusinessId(workingHoursId, businessId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("WORKING_HOURS_NOT_FOUND",
                        "No such working hours in this business.")));
    }

    private static EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getFullName(),
                employee.getServices().stream().map(ServiceOffering::getId).sorted().toList());
    }

    private static WorkingHoursResponse toResponse(WorkingHours hours) {
        return new WorkingHoursResponse(hours.getId(), hours.getWeekday(),
                hours.getStartTime(), hours.getEndTime());
    }
}
