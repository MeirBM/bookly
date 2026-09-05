package com.bookly.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByBusinessIdOrderByName(UUID businessId);

    /**
     * Both ids, always. The tenant guard proves the caller belongs to the business in the path, not
     * that this row does — turn-2 spec, pitfall 7. There is deliberately no unscoped findById here.
     */
    Optional<ServiceOffering> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndName(UUID businessId, String name);
}
