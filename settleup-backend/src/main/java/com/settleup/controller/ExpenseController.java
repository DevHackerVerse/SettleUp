package com.settleup.controller;

import com.settleup.dto.CreateExpenseRequest;
import com.settleup.dto.ExpenseResponse;
import com.settleup.entity.User;
import com.settleup.exception.UnauthorizedException;
import com.settleup.repository.UserRepository;
import com.settleup.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for expense endpoints (spec §5.3).
 *
 * POST   /api/v1/groups/{groupId}/expenses            → create expense (→ ledger entries)
 * GET    /api/v1/groups/{groupId}/expenses            → list expenses (paginated)
 * DELETE /api/v1/expenses/{transactionId}             → reversal (not hard-delete)
 */
@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    @PostMapping("/api/v1/groups/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable String groupId,
            @Valid @RequestBody CreateExpenseRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        ExpenseResponse response = expenseService.createExpense(groupId, req, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/groups/{groupId}/expenses")
    public ResponseEntity<Page<ExpenseResponse>> listExpenses(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        Page<ExpenseResponse> result = expenseService.listExpenses(groupId, currentUser, page, size);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/v1/expenses/{transactionId}")
    public ResponseEntity<Map<String, String>> reverseExpense(
            @PathVariable String transactionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        Map<String, String> result = expenseService.reverseExpense(transactionId, currentUser);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/v1/expenses/{transactionId}")
    public ResponseEntity<ExpenseResponse> editExpense(
            @PathVariable String transactionId,
            @Valid @RequestBody CreateExpenseRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        ExpenseResponse response = expenseService.editExpense(transactionId, req, currentUser);
        return ResponseEntity.ok(response);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
