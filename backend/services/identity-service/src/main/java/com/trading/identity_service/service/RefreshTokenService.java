package com.trading.identity_service.service;

import com.trading.identity_service.dto.TokenResponse;
import com.trading.identity_service.exception.InvalidRefreshTokenException;
import com.trading.identity_service.token.RefreshToken;
import com.trading.identity_service.token.RefreshTokenRepository;
import com.trading.identity_service.user.User;
import com.trading.identity_service.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Session lifecycle: start (login), rotate (refresh), revoke (logout / reuse detected). */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, JwtService jwtService,
                               @Value("${jwt.refresh-token-ttl}") Duration refreshTtl) {
        this.tokens = tokens;
        this.users = users;
        this.jwtService = jwtService;
        this.refreshTtl = refreshTtl;
    }

    /** Login: new session, first refresh token, expiry fixed at login + 30 days for the whole session. */
    @Transactional
    public String startSession(User user) {
        return issue(user.getId(), UUID.randomUUID(), Instant.now().plus(refreshTtl));
    }

    /**
     * Exchanges a refresh token for a new access + refresh pair.
     * noRollbackFor: when we detect reuse we revoke the session and THEN throw; without this, the
     * exception would roll the revocation back and the attacker's session would survive.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse rotate(String presentedToken) {
        JwtService.RefreshClaims claims = jwtService.parseRefreshToken(presentedToken);   // signature, expiry, kind
        RefreshToken row = tokens.findById(claims.tokenId()).orElseThrow(InvalidRefreshTokenException::new);

        // The JWT is valid, but is it the exact token we issued? Compare hashes in constant time.
        if (!MessageDigest.isEqual(sha256(presentedToken).getBytes(StandardCharsets.UTF_8),
                row.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRefreshTokenException();
        }

        Instant now = Instant.now();
        if (row.getRevokedAt() != null || !row.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        // Atomic "claim": only one caller can flip used_at from NULL. A loser means the token was used before.
        if (tokens.markUsed(row.getId(), now) == 0) {
            tokens.revokeSession(row.getSessionId(), now);   // reuse detected: assume theft, end the session
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(row.getUserId()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            tokens.revokeSession(row.getSessionId(), now);
            throw new InvalidRefreshTokenException();
        }

        // Same session, SAME expiry: rotation never extends the 30-day cap.
        String nextRefresh = issue(user.getId(), row.getSessionId(), row.getExpiresAt());
        return new TokenResponse(jwtService.issueAccessToken(user), nextRefresh, "Bearer",
                jwtService.accessTokenTtlSeconds());
    }

    /** Logout. Idempotent and silent: a bad or expired token just means there is nothing left to revoke. */
    @Transactional
    public void revokeSession(String presentedToken) {
        try {
            JwtService.RefreshClaims claims = jwtService.parseRefreshToken(presentedToken);
            tokens.findById(claims.tokenId())
                    .ifPresent(row -> tokens.revokeSession(row.getSessionId(), Instant.now()));
        } catch (InvalidRefreshTokenException ignored) {
            // nothing to revoke
        }
    }

    /** Log out from all devices. The caller is identified by a verified access token, never by a request field. */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        tokens.revokeAllForUser(userId, Instant.now());
    }

    private String issue(UUID userId, UUID sessionId, Instant expiresAt) {
        UUID tokenId = UUID.randomUUID();
        String token = jwtService.issueRefreshToken(userId, sessionId, tokenId, expiresAt);
        tokens.save(new RefreshToken(tokenId, sessionId, userId, sha256(token), expiresAt));
        return token;
    }

    /** SHA-256 is right here (not BCrypt): the input is a long random-signed value, not a guessable password. */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
