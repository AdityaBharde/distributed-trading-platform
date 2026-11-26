package com.trading.identity_service.dto;

import com.trading.identity_service.profile.UserProfile;
import com.trading.identity_service.user.User;

/** "My profile": account facts (read-only) plus preferences (editable). */
public record ProfileResponse(String email, String role, String theme, String defaultExchange, String timezone) {

    public static ProfileResponse from(User user, UserProfile profile) {
        return new ProfileResponse(user.getEmail(), user.getRole().name(),
                profile.getTheme(), profile.getDefaultExchange(), profile.getTimezone());
    }
}
