package com.bookly.business.dto;

import jakarta.validation.constraints.Size;

/**
 * Note what is absent, as in {@link CreateBusinessRequest}: no {@code businessId}. The business is
 * the one in the path, which the filter chain has already decided the caller belongs to.
 *
 * @param logoUrl an absolute {@code http(s)} URL, or null/blank to clear the logo and fall back to
 *                the Bookly mark
 */
public record UpdateBusinessLogoRequest(@Size(max = 2048) String logoUrl) {
}
