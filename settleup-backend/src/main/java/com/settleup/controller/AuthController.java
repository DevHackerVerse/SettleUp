package com.settleup.controller;

import com.settleup.dto.AuthResponse;
import com.settleup.dto.LoginRequest;
import com.settleup.dto.RefreshTokenRequest;
import com.settleup.dto.RegisterRequest;
import com.settleup.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints (spec §5.1).
 *
 * POST /api/v1/auth/register  → 201 with JWT pair
 * POST /api/v1/auth/login     → 200 with JWT pair
 * POST /api/v1/auth/refresh   → 200 with new access token
 *
 * All endpoints are public (no JWT required) — configured in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        AuthResponse response = authService.refresh(req);
        return ResponseEntity.ok(response);
    }
}
