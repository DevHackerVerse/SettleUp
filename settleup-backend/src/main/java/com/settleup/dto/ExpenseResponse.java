package com.settleup.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for expense creation and listing endpoints.
 */
public record ExpenseResponse(
        String transactionId,
        String groupId,
        String paidByUserId,
        String description,
        String totalAmount,       // formatted as "1200.00" per spec §5
        String currency,
        String splitType,
        boolean reversal,
        LocalDateTime createdAt,
        List<LedgerEntryDto> ledgerEntries
) {
    public record LedgerEntryDto(
            Long id,
            String userId,
            String entryType,     // "DEBIT" or "CREDIT"
            String amount         // formatted as "300.00"
    ) {}

    /** Convenience: format BigDecimal to 2-decimal String per spec requirement. */
    public static String formatAmount(BigDecimal bd) {
        return bd == null ? "0.00" : bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
