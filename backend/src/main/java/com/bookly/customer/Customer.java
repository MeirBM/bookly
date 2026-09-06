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

    /**
     * Fills in details that are missing, and never replaces details that are present.
     *
     * <p>The first version overwrote both. Booking is anonymous and keyed on an unverified email,
     * so anyone who could guess a client's address could rewrite that client's row — and because
     * the owner's list joins the customer by id, the attacker's name and phone would then appear
     * against every appointment that person had ever had, past and future, with the real phone
     * number gone rather than shadowed. One valid booking was the whole cost.
     *
     * <p>Filling blanks is still useful: a customer who first booked without a phone can add one.
     * Changing details that already exist is the owner's to do, not a stranger's.
     */
    public void fillMissingContactDetails(String phone) {
        if ((this.phone == null || this.phone.isBlank()) && phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
    }

    /** Never the contact details: a log line is not the place for a customer's email. */
    @Override
    public String toString() {
        return "Customer{id=" + id + ", businessId=" + businessId + "}";
    }
}
