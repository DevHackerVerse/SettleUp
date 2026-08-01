package com.settleup.dto;

import java.util.List;

/**
 * Response body for GET /api/v1/groups/{groupId}/simplified-debts.
 *
 * Each entry says: "from" user should pay "amount" to "to" user.
 */
public record SimplifiedDebtResponse(List<SettlementSuggestion> settlementsSuggested) {

    public record SettlementSuggestion(
            String fromUserId,
            String from,        // display name
            String toUserId,
            String to,          // display name
            String amount       // formatted as "300.00"
    ) {}
}
