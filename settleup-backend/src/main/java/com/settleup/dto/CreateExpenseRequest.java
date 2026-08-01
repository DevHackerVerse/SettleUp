package com.settleup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for POST /api/v1/groups/{groupId}/expenses.
 *
 * splits is required for PERCENTAGE and CUSTOM split types.
 * For EQUAL, splits can be omitted (all group members share equally).
 */
public record CreateExpenseRequest(

        @NotBlank(message = "description is required")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "totalAmount must have at most 2 decimal places")
        BigDecimal totalAmount,

        @NotBlank(message = "paidBy userId is required")
        String paidBy,

        @NotNull(message = "splitType is required")
        SplitTypeDto splitType,

        @Valid
        List<SplitEntry> splits,

        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code")
        String currency,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "expenseDate must be in YYYY-MM-DD format")
        String expenseDate

) {
    public enum SplitTypeDto {
        EQUAL, PERCENTAGE, CUSTOM
    }

    /**
     * A single split entry — for PERCENTAGE the value is a percentage (e.g. 25.00),
     * for CUSTOM the value is an absolute amount in the group's currency.
     */
    public record SplitEntry(
            @NotBlank(message = "userId is required in split entry")
            String userId,

            @NotNull(message = "value is required in split entry")
            @DecimalMin(value = "0.01", message = "split value must be greater than 0")
            BigDecimal value
    ) {}
}
