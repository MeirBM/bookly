package com.bookly.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Note what is absent: there is no {@code businessId} and no {@code ownerId}. The owner is the
 * authenticated caller. A field a client could set to someone else's id is the whole IDOR family
 * waiting to happen — criterion 1.11.
 *
 * @param timezone IANA zone id, e.g. {@code Asia/Jerusalem}
 */
public record CreateBusinessRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String timezone) {
}
