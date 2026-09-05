package com.bookly.availability;

import com.bookly.availability.dto.AvailabilityDtos.BlockedTimeResponse;
import com.bookly.availability.dto.AvailabilityDtos.CreateBlockedTime;
import com.bookly.common.error.ApiException;
import com.bookly.employee.EmployeeDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockedTimeService {

    private final BlockedTimeRepository repository;
    private final EmployeeDirectory employees;

    public BlockedTimeService(BlockedTimeRepository repository, EmployeeDirectory employees) {
        this.repository = repository;
        this.employees = employees;
    }

    @Transactional
    public BlockedTimeResponse create(UUID businessId, CreateBlockedTime request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("INVALID_BLOCKED_WINDOW",
                    "A blocked period must end after it starts.");
        }
        // A null employee means the whole business is blocked - a public holiday. A non-null one is
        // checked against this business, so another tenant's employee cannot be blocked by id.
        if (request.employeeId() != null) {
            employees.require(businessId, request.employeeId());
        }
        return toResponse(repository.save(new BlockedTime(businessId, request.employeeId(),
                request.startsAt(), request.endsAt(), request.reason())));
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> list(UUID businessId) {
        return repository.findByBusinessIdOrderByStartsAtAsc(businessId).stream()
                .map(BlockedTimeService::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID businessId, UUID blockedTimeId) {
        repository.delete(repository.findByIdAndBusinessId(blockedTimeId, businessId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("BLOCKED_TIME_NOT_FOUND",
                        "No such blocked period in this business.")));
    }

    private static BlockedTimeResponse toResponse(BlockedTime blocked) {
        return new BlockedTimeResponse(blocked.getId(), blocked.getEmployeeId(),
                blocked.getStartsAt(), blocked.getEndsAt(), blocked.getReason());
    }
}
