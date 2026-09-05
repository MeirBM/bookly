package com.bookly.business;

import com.bookly.auth.BooklyPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The one place tenant access is decided.
 *
 * <p>Referenced from {@code @PreAuthorize("@tenantGuard.canAccess(#businessId)")} on every
 * tenant-scoped route. There is deliberately a single implementation and a single call shape, so
 * that "who may read this business" has one answer and one place to audit.
 *
 * <p>Note what it does not do: it never reads a business id from a request body, and it never
 * trusts a claim inside the access token. Membership is looked up per request, so revoking someone
 * takes effect immediately rather than when their token happens to expire.
 */
@Component("tenantGuard")
public class TenantGuard {

    private final BusinessMemberRepository memberRepository;

    public TenantGuard(BusinessMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public boolean canAccess(UUID businessId) {
        if (businessId == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof BooklyPrincipal principal)) {
            return false;
        }
        // A business that does not exist has no members, so this returns false for both the absent
        // and the forbidden case. That is what makes them indistinguishable — criterion 1.12.
        return memberRepository.existsByBusinessIdAndUserId(businessId, principal.userId());
    }
}
