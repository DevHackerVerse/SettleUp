package com.settleup.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for group endpoints.
 * members is populated for the detail endpoint; null/empty for the list endpoint.
 */
public record GroupResponse(
        String groupId,
        String name,
        String description,
        String defaultCurrency,
        BigDecimal budgetAmount,
        Short budgetAlertThresholdPct,
        String createdByUserId,
        LocalDateTime createdAt,
        List<MemberEntry> members
) {
    public record MemberEntry(
            String userId,
            String name,
            String email,
            String role
    ) {}
}
