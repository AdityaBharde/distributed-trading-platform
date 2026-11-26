package com.trading.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login input. Sizes are capped only to bound the request payload; no password policy is revealed here. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 256) String password) {
}
