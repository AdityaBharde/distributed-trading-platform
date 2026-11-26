package com.trading.identity_service.exception;

/** Redis (login throttling) is unreachable. We fail closed: no throttling means no logins. */
public class AuthUnavailableException extends RuntimeException {
    public AuthUnavailableException(Throwable cause) {
        super("Login is temporarily unavailable", cause);
    }
}
