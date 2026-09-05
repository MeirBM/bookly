package com.bookly.common.error;

import org.springframework.http.HttpStatus;

/** An expected failure with a chosen status and a stable client-facing code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * The only authentication failure this application produces.
     *
     * <p>Deliberately carries no argument: criterion 1.5 requires that a wrong password and an
     * unknown email return byte-identical bodies. Letting a caller pass a message here is how that
     * guarantee gets lost six months from now.
     */
    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email or password is incorrect.");
    }

    public static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "The refresh token is not valid.");
    }

    /**
     * Returned both when a business does not exist and when the caller is not a member of it.
     *
     * <p>Criterion 1.12: distinguishing the two tells an attacker which business ids are real.
     */
    public static ApiException noBusinessAccess() {
        return new ApiException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED",
                "You do not have access to this business.");
    }
}
