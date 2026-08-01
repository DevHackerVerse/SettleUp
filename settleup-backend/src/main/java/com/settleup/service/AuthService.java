package com.settleup.service;

import com.settleup.dto.AuthResponse;
import com.settleup.dto.LoginRequest;
import com.settleup.dto.RefreshTokenRequest;
import com.settleup.dto.RegisterRequest;
import com.settleup.entity.User;
import com.settleup.exception.ConflictException;
import com.settleup.exception.UnauthorizedException;
import com.settleup.repository.UserRepository;
import com.settleup.security.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration, login, and token refresh.
 *
 * Security rules (spec §11):
 *  - Passwords hashed with BCrypt, never stored plain.
 *  - Access token: 15 min. Refresh token: 7 days.
 *  - JWT secret read from env var JWT_SECRET.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new user.
     * Throws {@link ConflictException} if the email is already taken.
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email is already registered: " + req.email());
        }

        User user = User.builder()
                .name(req.name())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .premium(false)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: id={} email={}", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    /**
     * Authenticates a user and returns a JWT pair.
     * Spring Security's AuthenticationManager handles bad-credentials detection.
     */
    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return buildAuthResponse(user);
    }

    /**
     * Validates a refresh token and issues a new access token.
     */
    public AuthResponse refresh(RefreshTokenRequest req) {
        String refreshToken = req.refreshToken();
        try {
            if (jwtProvider.isTokenExpired(refreshToken)) {
                throw new UnauthorizedException("Refresh token has expired — please log in again");
            }
            String email = jwtProvider.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("User not found for refresh token"));

            String newAccessToken = jwtProvider.generateAccessToken(
                    user.getPublicId().toString(), user.getEmail());

            return AuthResponse.accessTokenOnly(newAccessToken);
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String publicIdStr = user.getPublicId().toString();
        String accessToken  = jwtProvider.generateAccessToken(publicIdStr, user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(publicIdStr, user.getEmail());
        return new AuthResponse(publicIdStr, accessToken, refreshToken, user.getName(), user.getEmail());
    }
}
