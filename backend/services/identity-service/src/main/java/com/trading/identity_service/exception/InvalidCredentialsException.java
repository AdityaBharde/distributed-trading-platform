package com.trading.identity_service.exception;

/** One message for every login failure (unknown email, wrong password, disabled) so attackers learn nothing. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
