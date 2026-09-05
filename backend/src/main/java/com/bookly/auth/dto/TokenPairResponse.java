package com.bookly.auth.dto;

/**
 * @param expiresInSeconds lifetime of the access token, so a client can refresh ahead of expiry
 *                         rather than discovering it through a failed request
 */
public record TokenPairResponse(String accessToken, String refreshToken, long expiresInSeconds) {

    /**
     * Responses carry credentials too. Spring logs handler return values as well as arguments, so
     * the generated toString would put a refresh token in the log by exactly the mechanism that put
     * a password there - and a refresh token buys a new access token on demand.
     */
    @Override
    public String toString() {
        return "TokenPairResponse[expiresInSeconds=" + expiresInSeconds
                + ", accessToken=****, refreshToken=****]";
    }
}
