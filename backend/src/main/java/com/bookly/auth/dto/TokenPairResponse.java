package com.bookly.auth.dto;

/**
 * @param expiresInSeconds lifetime of the access token, so a client can refresh ahead of expiry
 *                         rather than discovering it through a failed request
 */
public record TokenPairResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
