package com.trading.identity_service.exception;

/** Every refresh failure (bad signature, expired, unknown, reused, revoked) looks the same to the caller. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
