package com.bookly.business;

import com.bookly.business.dto.BusinessResponse;
import com.bookly.business.dto.CreateBusinessRequest;
import com.bookly.common.error.ApiException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessService.class);

    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;

    public BusinessService(BusinessRepository businessRepository,
                           BusinessMemberRepository memberRepository) {
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public BusinessResponse create(CreateBusinessRequest request, UUID creatorUserId) {
        validateTimezone(request.timezone());

        String slug = SlugGenerator.generate(request.name(), businessRepository::existsBySlug);
        Business business = businessRepository.saveAndFlush(
                new Business(request.name().trim(), slug, request.timezone()));

        // The creator's membership is written in the same transaction as the business. If it were
        // a second step, a failure between them would leave a business nobody can administer.
        memberRepository.save(
                new BusinessMember(business.getId(), creatorUserId, Role.BUSINESS_OWNER));

        log.info("Created business {} (slug {}) owned by user {}",
                business.getId(), slug, creatorUserId);
        return BusinessResponse.from(business);
    }

    /**
     * Reads a business the caller is already known to be a member of.
     *
     * <p>{@code TenantGuard} has run before this method, in the security filter chain. The
     * {@code orElseThrow} therefore only fires in a race where the business was deleted between the
     * two, and it returns the same denial rather than a 404 — criterion 1.12.
     */
    @Transactional(readOnly = true)
    public BusinessResponse get(UUID businessId) {
        return businessRepository.findById(businessId)
                .map(BusinessResponse::from)
                .orElseThrow(ApiException::noBusinessAccess);
    }

    /** Every business the caller is a member of, and only those. */
    @Transactional(readOnly = true)
    public List<BusinessResponse> listForUser(UUID userId) {
        List<UUID> businessIds = memberRepository.findByUserId(userId).stream()
                .map(BusinessMember::getBusinessId)
                .toList();
        return businessRepository.findAllById(businessIds).stream()
                .map(BusinessResponse::from)
                .toList();
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw ApiException.badRequest("UNKNOWN_TIMEZONE",
                    "Not a recognised IANA time zone identifier.");
        }
    }
}
