package com.settleup.service;

import com.settleup.dto.BalanceResponse;
import com.settleup.entity.User;
import com.settleup.repository.LedgerEntryRepository;
import com.settleup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes derived balances from the ledger.
 *
 * Phase 2 — Redis caching:
 *   Computed balances are cached under key "balances:{groupId}" with a 10-minute TTL.
 *   Cache is INVALIDATED (not updated) whenever a new ledger entry is written,
 *   ensuring the next read always recomputes from the DB.
 *
 * Core accounting rule (spec §3):
 *   balance = SUM(CREDIT amount) − SUM(DEBIT amount) per user/group
 *   Positive = others owe this user.
 *   Negative = this user owes others.
 *
 * No mutable balance column is maintained anywhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    static final String CACHE_KEY_PREFIX = "balances:";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Returns the group's balance list.
     * Served from Redis cache if present; otherwise computed from DB and cached.
     *
     * @param groupId internal group PK
     */
    @Transactional(readOnly = true)
    public BalanceResponse computeGroupBalances(Long groupId) {
        String cacheKey = CACHE_KEY_PREFIX + groupId;

        // 1. Try cache
        Object cached = safeRedisGet(cacheKey);
        if (cached instanceof BalanceResponse response) {
            log.debug("Cache HIT for group balances: groupId={}", groupId);
            return response;
        }

        // 2. Miss — compute from DB
        log.debug("Cache MISS for group balances: groupId={}", groupId);
        BalanceResponse response = computeFromDb(groupId);

        // 3. Write to cache
        safeRedisSet(cacheKey, response, CACHE_TTL);

        return response;
    }

    /**
     * Invalidates the balance cache for a group.
     * Must be called after every new ledger entry write (expense create / reversal / settlement).
     *
     * @param groupId internal group PK
     */
    public void invalidateBalanceCache(Long groupId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + groupId);
            log.debug("Invalidated balance cache for groupId={}", groupId);
        } catch (Exception e) {
            // Cache errors must NEVER break write paths — log and continue
            log.warn("Failed to invalidate balance cache for groupId={}: {}", groupId, e.getMessage());
        }
    }

    /**
     * Returns the raw net balance map used by the debt simplification algorithm.
     * Does NOT use the cache (the map contains User objects which are not serialisable).
     * The cache is for the HTTP response layer only.
     */
    @Transactional(readOnly = true)
    public Map<User, BigDecimal> computeRawBalances(Long groupId) {
        List<Long> userIds = ledgerEntryRepository.findDistinctUserIdsByGroupId(groupId);
        Map<User, BigDecimal> result = new LinkedHashMap<>();

        for (Long userId : userIds) {
            BigDecimal netRaw = ledgerEntryRepository.calculateNetBalance(groupId, userId);
            BigDecimal net = (netRaw == null) ? BigDecimal.ZERO : netRaw;
            if (net.compareTo(BigDecimal.ZERO) == 0) continue;

            userRepository.findById(userId).ifPresent(u -> result.put(u, net));
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────

    private BalanceResponse computeFromDb(Long groupId) {
        List<Long> userIds = ledgerEntryRepository.findDistinctUserIdsByGroupId(groupId);
        List<BalanceResponse.BalanceEntry> entries = new ArrayList<>();

        for (Long userId : userIds) {
            BigDecimal net = ledgerEntryRepository.calculateNetBalance(groupId, userId);
            if (net == null) net = BigDecimal.ZERO;

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;

            entries.add(new BalanceResponse.BalanceEntry(
                    user.getPublicId().toString(),
                    user.getName(),
                    net.setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));
        }
        return new BalanceResponse(entries);
    }

    /** Graceful Redis GET — returns null on any error. */
    private Object safeRedisGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /** Graceful Redis SET — logs and ignores errors (cache is best-effort). */
    private void safeRedisSet(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed for key={}: {}", key, e.getMessage());
        }
    }
}
