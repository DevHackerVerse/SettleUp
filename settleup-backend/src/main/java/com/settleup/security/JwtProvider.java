package com.settleup.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT provider using jjwt 0.12.x.
 *
 * Spec requirements:
 *  - Access token expiry:  15 minutes  (900_000 ms)
 *  - Refresh token expiry: 7 days      (604_800_000 ms)
 *  - Secret read from env var JWT_SECRET (bound to app.jwt.secret)
 */
@Component
@Slf4j
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        // HMAC-SHA256 key derived from the configured secret
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    // ── Token generation ──────────────────────────────────────────

    public String generateAccessToken(String userPublicId, String email) {
        return buildToken(userPublicId, email, accessTokenExpiryMs, "access");
    }

    public String generateRefreshToken(String userPublicId, String email) {
        return buildToken(userPublicId, email, refreshTokenExpiryMs, "refresh");
    }

    private String buildToken(String subject, String email,
                               long expiryMs, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)                           // user public_id (UUID)
                .claim("email", email)
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiryMs)))
                .signWith(secretKey)
                .compact();
    }

    // ── Token parsing / validation ────────────────────────────────

    public Claims parseAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return parseAllClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return (String) parseAllClaims(token).get("email");
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseAllClaims(token);
            String email = (String) claims.get("email");
            return email.equals(userDetails.getUsername())
                    && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseAllClaims(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
}
