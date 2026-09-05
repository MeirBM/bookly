package com.bookly.auth;

import java.util.UUID;

/**
 * The authenticated caller.
 *
 * <p>Carries identity only. It deliberately does not carry a business id: tenant access is decided
 * per request from {@code business_members}, never from something the client influenced.
 */
public record BooklyPrincipal(UUID userId, String email) {
}
