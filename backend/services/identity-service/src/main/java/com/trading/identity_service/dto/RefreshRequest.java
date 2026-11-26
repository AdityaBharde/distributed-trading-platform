package com.trading.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of /auth/refresh and /auth/logout. (Step 8: the Next.js server will read this from a cookie.) */
public record RefreshRequest(@NotBlank @Size(max = 2048) String refreshToken) {
}
