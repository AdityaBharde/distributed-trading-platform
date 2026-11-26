package com.trading.identity_service.service;

import com.trading.identity_service.exception.AuthUnavailableException;
import com.trading.identity_service.exception.TooManyAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-account brute-force protection: after N failed logins for an email, that email is locked for a while.
 *
 * - The counter lives in Redis (fast, shared by all instances, expires by itself).
 * - It counts failures for ANY email string, existing or not, so a lockout reveals nothing about who has an account.
 * - The key is a hash of the email, so Redis does not hold readable emails.
 * - Every failure restarts the lock timer (the lock ends lockDuration after the LAST failure).
 * - Fail CLOSED: if Redis cannot be reached we refuse the login (503) rather than allow unlimited guessing.
 * - Tradeoff: an attacker can lock a victim out by failing on purpose. We accept that and keep the lock short.
 */
@Service
public class LoginAttemptService {

    private static final String PREFIX = "login:fail:";

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${login.max-failures}") int maxFailures,
                               @Value("${login.lock-duration}") Duration lockDuration) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    /** Call BEFORE touching the database or BCrypt, so a locked account also costs the attacker's CPU, not ours. */
    public void assertNotLocked(String normalizedEmail) {
        String key = key(normalizedEmail);
        try {
            String failures = redis.opsForValue().get(key);
            if (failures != null && Long.parseLong(failures) >= maxFailures) {
                Long secondsLeft = redis.getExpire(key);
                throw new TooManyAttemptsException(secondsLeft != null && secondsLeft > 0 ? secondsLeft : lockDuration.toSeconds());
            }
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }

    public void recordFailure(String normalizedEmail) {
        String key = key(normalizedEmail);
        try {
            redis.opsForValue().increment(key);   // atomic INCR, safe with many instances
            redis.expire(key, lockDuration);
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }

    public void reset(String normalizedEmail) {
        try {
            redis.delete(key(normalizedEmail));
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }

    private static String key(String normalizedEmail) {
        return PREFIX + RefreshTokenService.sha256(normalizedEmail);
    }
}
