package com.bookly.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /**
     * Written from the service's duration at booking time, not derived on read.
     *
     * <p>Criterion 3.9: changing a service's duration later must not silently move appointments
     * already made. What the customer agreed to is what is stored.
     */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Appointment() {
        // for JPA
    }

    public Appointment(UUID businessId, UUID employeeId, UUID serviceId, UUID customerId,
                       Instant startsAt, Instant endsAt, AppointmentStatus status) {
        this.businessId = businessId;
        this.employeeId = employeeId;
        this.serviceId = serviceId;
        this.customerId = customerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void changeStatus(AppointmentStatus next) {
        this.status = next;
    }

    void moveTo(Instant newStart, Instant newEnd) {
        this.startsAt = newStart;
        this.endsAt = newEnd;
    }
}
