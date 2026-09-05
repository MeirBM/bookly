package com.bookly.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The single error shape every failing request returns.
 *
 * <p>{@code code} is a stable string a client can branch on; {@code message} is for a human and
 * never carries a stack trace, a SQL fragment, or whether an account or business exists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }
}
