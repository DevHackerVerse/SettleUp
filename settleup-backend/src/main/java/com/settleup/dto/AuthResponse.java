package com.settleup.dto;

/**
 * Response body for register and login endpoints.
 * refreshToken is null for the /refresh endpoint (only a new access token is issued).
 */
public record AuthResponse(
        String userId,
        String token,
        String refreshToken,
        String name,
        String email
) {
    /** Factory for the /refresh response (no refreshToken in body). */
    public static AuthResponse accessTokenOnly(String token) {
        return new AuthResponse(null, token, null, null, null);
    }
}
