package com.deliveryapp.exception;

/**
 * The refresh token can no longer be used: the session is over and the user has to log in again.
 * Returned as HTTP 400 with {@code "code": "SESSION_EXPIRED"} so the app can tell it apart from
 * network or server errors, which must never log the user out.
 */
public class InvalidRefreshTokenException extends InvalidDataException {

    public static final String CODE = "SESSION_EXPIRED";

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
