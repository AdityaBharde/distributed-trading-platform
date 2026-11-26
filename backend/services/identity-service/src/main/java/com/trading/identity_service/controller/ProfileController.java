package com.trading.identity_service.controller;

import com.trading.identity_service.dto.ProfileResponse;
import com.trading.identity_service.dto.ProfileUpdateRequest;
import com.trading.identity_service.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "/profile/me": there is deliberately no user id in the path. The id comes from the verified token's "sub",
 * so a user cannot read or change someone else's profile by editing a URL (no IDOR).
 */
@RestController
@RequestMapping("/profile/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(UUID.fromString(jwt.getSubject()), request);
    }
}
