package com.trading.identity_service.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Atomically claims a token for rotation. Returns 1 if THIS call marked it used, 0 if it was
     * already used or revoked. Being one UPDATE with the conditions in the WHERE clause, two parallel
     * requests with the same token cannot both win.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** "Log out everywhere": kills every token of every session of one user. Uses the plain user_id column. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Kills every token of the login session (logout, or reuse detected). */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.sessionId = :sessionId and t.revokedAt is null")
    int revokeSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
}
