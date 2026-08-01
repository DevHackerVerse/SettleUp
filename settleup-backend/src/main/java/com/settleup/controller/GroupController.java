package com.settleup.controller;

import com.settleup.dto.*;
import com.settleup.entity.User;
import com.settleup.repository.UserRepository;
import com.settleup.service.DebtSimplificationService;
import com.settleup.service.GroupService;
import com.settleup.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for group management endpoints (spec §5.2).
 *
 * POST   /api/v1/groups                          → create group
 * GET    /api/v1/groups                          → list groups for current user
 * GET    /api/v1/groups/{groupId}                → group detail + members
 * POST   /api/v1/groups/{groupId}/members        → add member by email
 * GET    /api/v1/groups/{groupId}/balances        → derived balances
 * GET    /api/v1/groups/{groupId}/simplified-debts → debt simplification
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final LedgerService ledgerService;
    private final DebtSimplificationService debtSimplificationService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        GroupResponse response = groupService.createGroup(req, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> listGroups(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(groupService.getGroupsForUser(currentUser));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(groupService.getGroupDetail(groupId, currentUser));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Map<String, Boolean>> addMember(
            @PathVariable String groupId,
            @Valid @RequestBody AddMemberRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        groupService.addMember(groupId, req, currentUser);
        return ResponseEntity.ok(Map.of("added", true));
    }

    @GetMapping("/{groupId}/balances")
    public ResponseEntity<BalanceResponse> getBalances(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        // Membership check delegated to GroupService
        groupService.requireMembership(
                groupService.findGroupByPublicId(groupId).getId(),
                currentUser.getId());

        var group = groupService.findGroupByPublicId(groupId);
        BalanceResponse balances = ledgerService.computeGroupBalances(group.getId());
        return ResponseEntity.ok(balances);
    }

    @GetMapping("/{groupId}/simplified-debts")
    public ResponseEntity<SimplifiedDebtResponse> getSimplifiedDebts(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        var group = groupService.findGroupByPublicId(groupId);
        groupService.requireMembership(group.getId(), currentUser.getId());

        Map<User, BigDecimal> rawBalances = ledgerService.computeRawBalances(group.getId());
        SimplifiedDebtResponse debts = debtSimplificationService.simplify(rawBalances);
        return ResponseEntity.ok(debts);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new com.settleup.exception.UnauthorizedException("Authenticated user not found"));
    }
}
