package com.bookly.business;

import com.bookly.auth.BooklyPrincipal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The one place tenant access is decided.
 *
 * <p>Consulted from the security filter chain, not from {@code @PreAuthorize}. That ordering
 * matters: method security runs after Spring has resolved the handler's arguments, so a request
 * missing a required query parameter was answered 400 before anyone asked whether the caller was
 * entitled to the business at all. Authorization belongs before input validation, or an outsider
 * gets to probe what the endpoint accepts.
 *
 * <p>Moving it also removes the silent failure mode that {@code @PreAuthorize} carries: an
 * annotation that does nothing when method security is switched off, or when the method is called
 * from inside the same bean, looks exactly like one that works.
 *
 * <p>One implementation, one call shape, one place to audit.
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

    public boolean canAccess(Authentication authentication, UUID businessId) {
        if (businessId == null) {
            return false;
        }
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof BooklyPrincipal principal)) {
            return false;
        }
        // A business that does not exist has no members, so this returns false for both the absent
        // and the forbidden case. That is what makes them indistinguishable — criterion 1.12.
        return memberRepository.existsByBusinessIdAndUserId(businessId, principal.userId());
    }
}
