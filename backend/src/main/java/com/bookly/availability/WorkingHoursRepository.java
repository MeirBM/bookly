package com.bookly.availability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findByBusinessIdAndEmployeeIdOrderByWeekdayAscStartTimeAsc(
            UUID businessId, UUID employeeId);

    List<WorkingHours> findByBusinessIdAndEmployeeIdAndWeekdayOrderByStartTimeAsc(
            UUID businessId, UUID employeeId, short weekday);

    Optional<WorkingHours> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessIdAndEmployeeId(UUID businessId, UUID employeeId);
}
