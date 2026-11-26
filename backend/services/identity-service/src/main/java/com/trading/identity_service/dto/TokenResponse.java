package com.trading.identity_service.dto;

/**
 * OAuth2-style token response: clients send "Authorization: Bearer <accessToken>". The refreshToken is
 * only ever exchanged at /auth/refresh.
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
