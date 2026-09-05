package com.bookly.business;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessMemberRepository extends JpaRepository<BusinessMember, UUID> {

    /** The membership check behind every tenant-scoped request. */
    boolean existsByBusinessIdAndUserId(UUID businessId, UUID userId);

    List<BusinessMember> findByUserId(UUID userId);
}
