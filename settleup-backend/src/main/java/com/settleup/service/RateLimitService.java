package com.settleup.service;

import com.settleup.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed rate limiter (Phase 2, spec §9.2).
 *
 * Strategy: fixed-window counter per (userId, action) with a 1-minute window.
 *   Key pattern: "ratelimit:{action}:{userId}"
 *   On each call: INCR the key; if it's 1 (first call in window), set TTL to 60s.
 *   If count > limit → throw RateLimitExceededException (→ HTTP 429).
 *
 * Spec requirement: max 20 expenses/minute/user on expense creation endpoint.
 *
 * Design note: Redis errors degrade gracefully (allow the request through with a warning).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final int EXPENSE_CREATION_LIMIT = 20;  // per minute per user
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Checks and increments the expense-creation rate limit for a user.
     * Throws {@link RateLimitExceededException} if the limit is exceeded.
     *
     * @param userPublicId the user's public UUID string (used as the rate-limit key)
     */
    public void checkExpenseCreationLimit(String userPublicId) {
        checkLimit("expense_create", userPublicId, EXPENSE_CREATION_LIMIT);
    }

    // ── Internal ──────────────────────────────────────────────────────

    private void checkLimit(String action, String userId, int limit) {
        String key = "ratelimit:" + action + ":" + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("Redis INCR returned null for key={}; allowing request", key);
                return;
            }
            if (count == 1) {
                // First request in this window — set the TTL
                redisTemplate.expire(key, WINDOW);
            }
            if (count > limit) {
                log.warn("Rate limit exceeded: key={} count={} limit={}", key, count, limit);
                throw new RateLimitExceededException(
                        String.format("Rate limit exceeded: max %d %s requests per minute", limit, action));
            }
        } catch (RateLimitExceededException e) {
            throw e;  // re-throw without logging as a generic error
        } catch (Exception e) {
            // Redis error → fail open (allow the request) to avoid breaking the write path
            log.warn("Rate-limit check failed for key={}: {} — allowing request", key, e.getMessage());
        }
    }
}
