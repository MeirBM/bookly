package com.bookly.employee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    List<Employee> findByBusinessIdOrderByFullName(UUID businessId);

    Optional<Employee> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * The employees who can actually perform a service.
     *
     * <p>An employee not linked to it contributes no slots (criterion 2.6), so this query is what
     * makes that criterion true rather than a filter applied later and forgotten.
     */
    @Query("select e from Employee e join e.services s "
            + "where e.businessId = :businessId and s.id = :serviceId order by e.fullName")
    List<Employee> findEligibleFor(@Param("businessId") UUID businessId,
                                   @Param("serviceId") UUID serviceId);
}
