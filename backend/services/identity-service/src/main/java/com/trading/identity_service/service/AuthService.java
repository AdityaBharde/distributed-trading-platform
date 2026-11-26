package com.trading.identity_service.service;

import com.trading.identity_service.dto.TokenResponse;
import com.trading.identity_service.exception.EmailAlreadyUsedException;
import com.trading.identity_service.exception.InvalidCredentialsException;
import com.trading.identity_service.user.Role;
import com.trading.identity_service.user.User;
import com.trading.identity_service.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttempts;

    /** A real BCrypt hash we compare against when the email is unknown, so timing doesn't reveal it. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, LoginAttemptService loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttempts = loginAttempts;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public User register(String email, String rawPassword) {
        String normalized = User.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            // Role is fixed server-side. saveAndFlush so a unique-index violation surfaces here.
            return users.saveAndFlush(new User(normalized, passwordEncoder.encode(rawPassword), Role.USER));
        } catch (DataIntegrityViolationException race) {
            // Two simultaneous sign-ups passed the exists-check; the DB unique index decided the loser.
            throw new EmailAlreadyUsedException();
        }
    }

    public TokenResponse login(String email, String rawPassword) {
        String normalized = User.normalize(email);
        loginAttempts.assertNotLocked(normalized);   // 429 if locked, 503 if Redis is down; before any BCrypt work

        Optional<User> found = users.findByEmail(normalized);

        // Always run exactly one BCrypt comparison, whether or not the user exists.
        String hashToCheck = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (found.isEmpty() || !passwordMatches || !found.get().isEnabled()) {
            loginAttempts.recordFailure(normalized);
            throw new InvalidCredentialsException();
        }
        loginAttempts.reset(normalized);
        User user = found.get();
        return new TokenResponse(jwtService.issueAccessToken(user), refreshTokenService.startSession(user),
                "Bearer", jwtService.accessTokenTtlSeconds());
    }
}
