package com.settleup.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request body for POST /api/v1/groups/{groupId}/settlements.
 * Spec §5.4.
 */
public record CreateSettlementRequest(

        @NotBlank(message = "payeeId is required")
        String payeeId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        /**
         * Client-generated UUID for idempotency.
         * The same key sent twice must result in a no-op (not a double-settlement).
         */
        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey
) {}
