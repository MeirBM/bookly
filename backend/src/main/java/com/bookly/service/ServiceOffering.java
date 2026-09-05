package com.bookly.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.UUID;

/**
 * Something a business sells an appointment for: a haircut, a session, a consultation.
 *
 * <p>Named {@code ServiceOffering} rather than {@code Service} only because {@code Service} collides
 * with Spring's stereotype annotation in every file that touches both. The domain word is "service";
 * this is the one place the code deviates from it, and it deviates to stay readable.
 */
@Entity
@Table(name = "services")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "price_minor")
    private Long priceMinor;

    protected ServiceOffering() {
        // for JPA
    }

    public ServiceOffering(UUID businessId, String name, int durationMinutes, Long priceMinor) {
        this.businessId = businessId;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.priceMinor = priceMinor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Duration getDuration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public Long getPriceMinor() {
        return priceMinor;
    }
}
