package com.bookly.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * The appointments that occupy time for one employee in a window.
     *
     * <p>This is what turns a booking into a busy interval for the availability engine. It filters
     * on the same statuses the exclusion constraint names, so the calendar and the constraint agree
     * about what "taken" means — if they disagreed, availability would offer a slot the database
     * then refuses, which reads to a customer as the site being broken.
     */
    @Query("select a from Appointment a where a.businessId = :businessId "
            + "and a.employeeId = :employeeId "
            + "and a.status in (com.bookly.appointment.AppointmentStatus.PENDING, "
            + "                 com.bookly.appointment.AppointmentStatus.CONFIRMED) "
            + "and a.endsAt > :from and a.startsAt < :to")
    List<Appointment> findOccupying(@Param("businessId") UUID businessId,
                                    @Param("employeeId") UUID employeeId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("select a from Appointment a where a.businessId = :businessId "
            + "and a.startsAt >= :from and a.startsAt < :to order by a.startsAt")
    List<Appointment> findForBusinessBetween(@Param("businessId") UUID businessId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);
}
