package com.bookly.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsBySlug(String slug);

    Optional<Business> findBySlug(String slug);
}
