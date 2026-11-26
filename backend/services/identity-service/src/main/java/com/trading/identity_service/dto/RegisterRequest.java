package com.trading.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * Registration input. There is deliberately NO role field: clients must never choose their own role
 * (mass assignment). New accounts are always USER.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {

    /** BCrypt silently ignores bytes after the 72nd, so we reject longer passwords instead of truncating. */
    @JsonIgnore
    @AssertTrue(message = "must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
