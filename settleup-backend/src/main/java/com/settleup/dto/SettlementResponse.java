package com.settleup.dto;

import java.time.LocalDateTime;

/**
 * Response body for settlement endpoints (spec §5.4).
 *
 * status transitions: PENDING → PROCESSING → COMPLETED | FAILED
 * mockUpiRef is set only when status == COMPLETED.
 */
public record SettlementResponse(
        String settlementId,
        String groupId,
        String payerId,
        String payeeId,
        String amount,           // formatted as "300.00"
        String status,
        String idempotencyKey,
        String mockUpiRef,       // null until COMPLETED
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
