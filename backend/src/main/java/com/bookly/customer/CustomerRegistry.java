package com.bookly.customer;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds or creates the customer for a booking, in its own transaction.
 *
 * <p>{@code REQUIRES_NEW} is the point. Creating the customer inside the booking transaction meant
 * a race on the unique {@code (business_id, lower(email))} index surfaced where the caller was
 * looking for the appointment overlap constraint — two people sharing an address booking two
 * different free slots at the same moment produced a 500, with no overlap involved. Once a
 * transaction has hit a constraint violation it is rollback-only, so the row cannot simply be
 * re-read; it has to have been a separate transaction to begin with.
 */
@Component
public class CustomerRegistry {

    private final CustomerRepository customers;

    public CustomerRegistry(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID findOrCreate(UUID businessId, String fullName, String email, String phone) {
        String trimmed = email.trim();
        return customers.findByBusinessIdAndEmailIgnoreCase(businessId, trimmed)
                .map(existing -> {
                    existing.fillMissingContactDetails(phone);
                    return existing.getId();
                })
                .orElseGet(() -> create(businessId, fullName, trimmed, phone));
    }

    /**
     * Deliberately does not recover here.
     *
     * <p>The previous version caught the unique-key violation and re-read the row in the same
     * transaction. PostgreSQL had already aborted that transaction, so the read failed with
     * "current transaction is aborted, commands ignored" and escaped as a 500 — recovery attempted
     * inside a transaction already poisoned. The retry has to happen in a *new* transaction, so it
     * belongs to the caller, one level up, where this method can simply be invoked again.
     */
    private UUID create(UUID businessId, String fullName, String email, String phone) {
        return customers.saveAndFlush(new Customer(businessId, fullName, email, phone)).getId();
    }
}
