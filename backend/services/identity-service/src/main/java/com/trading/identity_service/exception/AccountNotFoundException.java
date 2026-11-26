package com.trading.identity_service.exception;

/** A valid token whose account no longer exists (deleted after the token was issued). */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() {
        super("Account not found");
    }
}
