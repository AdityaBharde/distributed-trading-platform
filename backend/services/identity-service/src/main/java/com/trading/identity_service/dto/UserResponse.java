package com.trading.identity_service.dto;

import com.trading.identity_service.user.User;

import java.util.UUID;

/** What we return about an account. Never the password hash. */
public record UserResponse(UUID id, String email, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
