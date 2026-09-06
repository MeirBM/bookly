package com.bookly.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Someone who books.
 *
 * <p>Scoped to one business: the same person booking two salons is two rows, because one salon has
 * no business knowing the other's clientele. No login — requiring an account is exactly the friction
 * the problem statement objects to.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column
    private String phone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
        // for JPA
    }

    public Customer(UUID businessId, String fullName, String email, String phone) {
        this.businessId = businessId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public void updateContactDetails(String fullName, String phone) {
        this.fullName = fullName;
        if (phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
    }

    /** Never the contact details: a log line is not the place for a customer's email. */
    @Override
    public String toString() {
        return "Customer{id=" + id + ", businessId=" + businessId + "}";
    }
}
