package com.bookly.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** Case-insensitive, matching the unique index in V5: one email is one customer here. */
    Optional<Customer> findByBusinessIdAndEmailIgnoreCase(UUID businessId, String email);

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);
}
