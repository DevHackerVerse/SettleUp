package com.settleup.controller;

import com.settleup.dto.CreateSettlementRequest;
import com.settleup.dto.SettlementResponse;
import com.settleup.entity.User;
import com.settleup.exception.UnauthorizedException;
import com.settleup.repository.UserRepository;
import com.settleup.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for settlement endpoints (spec §5.4).
 *
 * POST /api/v1/groups/{groupId}/settlements
 *   → Creates settlement, enqueues to RabbitMQ, returns 202 ACCEPTED immediately.
 *
 * GET  /api/v1/settlements/{settlementId}
 *   → Returns current status (client polls until COMPLETED or FAILED).
 */
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final UserRepository userRepository;

    @PostMapping("/api/v1/groups/{groupId}/settlements")
    public ResponseEntity<SettlementResponse> initiateSettlement(
            @PathVariable String groupId,
            @Valid @RequestBody CreateSettlementRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        SettlementResponse response = settlementService.initiate(groupId, req, currentUser);
        // 202 Accepted — settlement is async; client should poll GET to track status
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/api/v1/settlements/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(
            @PathVariable String settlementId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(settlementService.getSettlement(settlementId, currentUser));
    }

    // ── Helper ────────────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
