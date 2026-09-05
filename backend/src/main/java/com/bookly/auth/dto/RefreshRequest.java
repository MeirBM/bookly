package com.bookly.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {

    /** A refresh token is a credential too: it buys a new access token on demand. */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=****]";
    }
}
