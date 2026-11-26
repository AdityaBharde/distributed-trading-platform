package com.trading.identity_service.service;

import com.trading.identity_service.exception.AuthUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Redis outage: login must FAIL CLOSED (refuse) instead of silently skipping the brute-force protection. */
class LoginAttemptServiceTest {

    private LoginAttemptService serviceWithRedisDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis is down"));
        when(redis.delete(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));
        return new LoginAttemptService(redis, 5, Duration.ofMinutes(15));
    }

    @Test
    void checkingTheLockFailsClosedWhenRedisIsDown() {
        assertThrows(AuthUnavailableException.class, () -> serviceWithRedisDown().assertNotLocked("a@example.com"));
    }

    @Test
    void recordingAFailureFailsClosedWhenRedisIsDown() {
        assertThrows(AuthUnavailableException.class, () -> serviceWithRedisDown().recordFailure("a@example.com"));
    }

    @Test
    void resetFailsClosedWhenRedisIsDown() {
        assertThrows(AuthUnavailableException.class, () -> serviceWithRedisDown().reset("a@example.com"));
    }
}
