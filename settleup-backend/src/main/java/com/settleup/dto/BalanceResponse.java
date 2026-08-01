package com.settleup.dto;

import java.util.List;

/**
 * Response body for GET /api/v1/groups/{groupId}/balances.
 *
 * netBalance is a String formatted to 2 decimals.
 * Positive = others owe this user. Negative = this user owes others.
 */
public record BalanceResponse(List<BalanceEntry> balances) {

    public record BalanceEntry(
            String userId,
            String name,
            String netBalance    // e.g. "900.00" or "-300.00"
    ) {}
}
