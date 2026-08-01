package com.settleup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for POST /api/v1/groups.
 */
public record CreateGroupRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code")
        String currency,

        /** Optional; null means no budget cap. Must be positive if provided. */
        BigDecimal budgetAmount
) {}
