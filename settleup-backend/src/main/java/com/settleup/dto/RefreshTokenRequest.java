package com.settleup.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/refresh.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
