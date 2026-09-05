package com.bookly.availability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedTimeRepository extends JpaRepository<BlockedTime, UUID> {

    List<BlockedTime> findByBusinessIdOrderByStartsAtAsc(UUID businessId);

    Optional<BlockedTime> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * Everything blocking one employee in a window: their own entries plus the business-wide ones.
     *
     * <p>Half-open comparison, matching {@link BusyInterval}: an entry ending exactly when the
     * window opens does not block it.
     */
    @Query("select b from BlockedTime b where b.businessId = :businessId "
            + "and (b.employeeId = :employeeId or b.employeeId is null) "
            + "and b.endsAt > :from and b.startsAt < :to")
    List<BlockedTime> findOverlapping(@Param("businessId") UUID businessId,
                                      @Param("employeeId") UUID employeeId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);
}
