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

    private UUID create(UUID businessId, String fullName, String email, String phone) {
        try {
            return customers.saveAndFlush(new Customer(businessId, fullName, email, phone)).getId();
        } catch (DataIntegrityViolationException ex) {
            // Somebody else inserted the same email between the read and the write. The row now
            // exists, which is the outcome we wanted; a fresh transaction can read it.
            return customers.findByBusinessIdAndEmailIgnoreCase(businessId, email)
                    .map(Customer::getId)
                    .orElseThrow(() -> ex);
        }
    }
}
