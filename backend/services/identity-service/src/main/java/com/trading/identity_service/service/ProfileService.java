package com.trading.identity_service.service;

import com.trading.identity_service.dto.ProfileResponse;
import com.trading.identity_service.dto.ProfileUpdateRequest;
import com.trading.identity_service.exception.AccountNotFoundException;
import com.trading.identity_service.profile.UserProfile;
import com.trading.identity_service.profile.UserProfileRepository;
import com.trading.identity_service.user.User;
import com.trading.identity_service.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;

    public ProfileService(UserRepository users, UserProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional
    public ProfileResponse get(UUID userId) {
        User user = users.findById(userId).orElseThrow(AccountNotFoundException::new);
        return ProfileResponse.from(user, loadOrCreate(userId));
    }

    @Transactional
    public ProfileResponse update(UUID userId, ProfileUpdateRequest request) {
        User user = users.findById(userId).orElseThrow(AccountNotFoundException::new);
        UserProfile profile = loadOrCreate(userId);
        // A managed entity: the change is written when the transaction commits (dirty checking), no save() needed.
        profile.update(request.theme(), request.defaultExchange(), request.timezone());
        return ProfileResponse.from(user, profile);
    }

    private UserProfile loadOrCreate(UUID userId) {
        profiles.insertDefaultsIfMissing(userId);
        return profiles.findById(userId).orElseThrow(AccountNotFoundException::new);
    }
}
