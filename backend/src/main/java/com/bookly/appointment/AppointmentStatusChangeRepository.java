package com.bookly.appointment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentStatusChangeRepository
        extends JpaRepository<AppointmentStatusChange, UUID> {

    List<AppointmentStatusChange> findByAppointmentIdOrderByChangedAtAsc(UUID appointmentId);

    long countByAppointmentId(UUID appointmentId);
}
