package com.settleup.service;

import com.settleup.dto.BalanceResponse;
import com.settleup.dto.CreateExpenseRequest;
import com.settleup.dto.ExpenseResponse;
import com.settleup.dto.ExpenseResponse.LedgerEntryDto;
import com.settleup.entity.*;
import com.settleup.entity.ExpenseTransaction.SplitType;
import com.settleup.entity.LedgerEntry.EntryType;
import com.settleup.exception.BadRequestException;
import com.settleup.exception.ForbiddenException;
import com.settleup.exception.ResourceNotFoundException;
import com.settleup.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Handles expense creation, listing, and reversal.
 *
 * CORE LEDGER RULE (spec §3):
 *   Every expense creates paired LedgerEntry rows such that SUM(debits) == SUM(credits).
 *
 *   Example — Karan pays ₹1200, split equally 4 ways (Karan, Aman, Riya, Sam):
 *     CREDIT Karan  ₹1200  (he paid out)
 *     DEBIT  Karan  ₹300   (his own share)
 *     DEBIT  Aman   ₹300
 *     DEBIT  Riya   ₹300
 *     DEBIT  Sam    ₹300
 *   → Karan net = 1200 − 300 = +900. Others net = −300 each.
 *
 * Corrections via reversal: "DELETE" creates a reversal transaction with equal-and-opposite
 * entries; original rows are NEVER touched (spec §3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseTransactionRepository expenseTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    // Phase 2 — rate limiting, caching, WebSocket
    private final RateLimitService rateLimitService;
    private final LedgerService ledgerService;
    private final WebSocketEventService webSocketEventService;

    // ── Create ────────────────────────────────────────────────────────

    /**
     * Creates an expense transaction and writes the ledger entries.
     */
    @Transactional
    public ExpenseResponse createExpense(String groupPublicId,
                                         CreateExpenseRequest req,
                                         User creator) {
        // ── Phase 2: rate limit check (max 20 expenses/min/user) ──────
        rateLimitService.checkExpenseCreationLimit(creator.getPublicId().toString());

        Group group = findGroup(groupPublicId);
        requireMembership(group.getId(), creator.getId());

        // Resolve the payer
        User paidBy = userRepository.findByPublicId(UUID.fromString(req.paidBy()))
                .orElseThrow(() -> new ResourceNotFoundException("Payer user not found: " + req.paidBy()));
        requireMembership(group.getId(), paidBy.getId());

        String currency = (req.currency() != null) ? req.currency() : group.getDefaultCurrency();
        SplitType splitType = SplitType.valueOf(req.splitType().name());

        // Compute the per-member amounts based on split type
        List<SplitAllocation> allocations = computeAllocations(req, group, splitType);

        // Validate that allocations sum to totalAmount (critical correctness check)
        BigDecimal allocSum = allocations.stream()
                .map(SplitAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocSum.compareTo(req.totalAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BadRequestException(
                    String.format("Split amounts sum to %s but totalAmount is %s",
                            allocSum.toPlainString(), req.totalAmount().toPlainString()));
        }

        LocalDateTime customCreatedAt = null;
        if (req.expenseDate() != null && !req.expenseDate().isBlank()) {
            try {
                customCreatedAt = java.time.LocalDate.parse(req.expenseDate())
                        .atTime(java.time.LocalTime.now());
            } catch (Exception e) {
                log.warn("Invalid expenseDate format: {}", req.expenseDate());
            }
        }

        // Persist the transaction header
        ExpenseTransaction txn = ExpenseTransaction.builder()
                .group(group)
                .paidBy(paidBy)
                .description(req.description())
                .totalAmount(req.totalAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .splitType(splitType)
                .createdBy(creator)
                .createdAt(customCreatedAt)
                .build();
        txn = expenseTransactionRepository.save(txn);

        // Write ledger entries
        List<LedgerEntry> entries = writeLedgerEntries(txn, group, paidBy, allocations);

        log.info("Created expense txnId={} groupId={} totalAmount={}",
                txn.getPublicId(), group.getPublicId(), req.totalAmount());

        ExpenseResponse response = toExpenseResponse(txn, entries, false);

        // ── Phase 2: invalidate cache + push WebSocket update ─────────
        String groupPublicIdStr = group.getPublicId().toString();
        ledgerService.invalidateBalanceCache(group.getId());
        BalanceResponse updatedBalances = ledgerService.computeGroupBalances(group.getId());
        webSocketEventService.broadcastExpenseAdded(
                groupPublicIdStr, txn.getPublicId().toString(), updatedBalances);

        return response;
    }

    // ── List ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listExpenses(String groupPublicId, User currentUser, int page, int size) {
        Group group = findGroup(groupPublicId);
        requireMembership(group.getId(), currentUser.getId());

        Pageable pageable = PageRequest.of(page, size);
        return expenseTransactionRepository
                .findByGroupIdOrderByCreatedAtDesc(group.getId(), pageable)
                .map(txn -> {
                    List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(txn.getId());
                    boolean isReversal = (txn.getReversedTransaction() != null);
                    return toExpenseResponse(txn, entries, isReversal);
                });
    }

    // ── Reverse (spec: "DELETE" creates a reversal) ───────────────────

    /**
     * Creates a reversal transaction — equal and opposite ledger entries.
     * The original rows are NEVER modified.
     */
    @Transactional
    public Map<String, String> reverseExpense(String transactionPublicId, User currentUser) {
        ExpenseTransaction original = expenseTransactionRepository
                .findByPublicId(UUID.fromString(transactionPublicId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Expense not found: " + transactionPublicId));

        requireMembership(original.getGroup().getId(), currentUser.getId());

        // Guard: ensure this transaction hasn't already been reversed
        boolean alreadyReversed = expenseTransactionRepository
                .findByGroupIdOrderByCreatedAtDesc(original.getGroup().getId(), PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .anyMatch(t -> t.getReversedTransaction() != null
                        && t.getReversedTransaction().getId().equals(original.getId()));
        if (alreadyReversed) {
            throw new BadRequestException("This expense has already been reversed");
        }

        // Create reversal transaction
        ExpenseTransaction reversalTxn = ExpenseTransaction.builder()
                .group(original.getGroup())
                .paidBy(original.getPaidBy())
                .description("REVERSAL: " + original.getDescription())
                .totalAmount(original.getTotalAmount())
                .currency(original.getCurrency())
                .splitType(original.getSplitType())
                .reversedTransaction(original)
                .createdBy(currentUser)
                .build();
        reversalTxn = expenseTransactionRepository.save(reversalTxn);

        // Mirror original entries with flipped entry_type
        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(original.getId());
        for (LedgerEntry orig : originalEntries) {
            EntryType flipped = (orig.getEntryType() == EntryType.DEBIT) ? EntryType.CREDIT : EntryType.DEBIT;
            LedgerEntry reversal = LedgerEntry.builder()
                    .transaction(reversalTxn)
                    .group(orig.getGroup())
                    .accountUser(orig.getAccountUser())
                    .entryType(flipped)
                    .amount(orig.getAmount())
                    .build();
            ledgerEntryRepository.save(reversal);
        }

        log.info("Reversed expense originalTxnId={} reversalTxnId={}",
                original.getPublicId(), reversalTxn.getPublicId());

        // ── Phase 2: invalidate cache + push WebSocket update ─────────
        ledgerService.invalidateBalanceCache(original.getGroup().getId());
        BalanceResponse updatedBalances = ledgerService.computeGroupBalances(original.getGroup().getId());
        webSocketEventService.broadcastExpenseAdded(
                original.getGroup().getPublicId().toString(),
                reversalTxn.getPublicId().toString(),
                updatedBalances);

        return Map.of("reversalTransactionId", reversalTxn.getPublicId().toString());
    }

    /**
     * Edits an existing expense by reversing the original transaction and creating a new updated one.
     */
    @Transactional
    public ExpenseResponse editExpense(String transactionPublicId, CreateExpenseRequest req, User currentUser) {
        ExpenseTransaction original = expenseTransactionRepository
                .findByPublicId(UUID.fromString(transactionPublicId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Expense not found: " + transactionPublicId));

        reverseExpense(transactionPublicId, currentUser);
        return createExpense(original.getGroup().getPublicId().toString(), req, currentUser);
    }

    // ── Ledger entry generation ───────────────────────────────────────

    /**
     * Writes one CREDIT (for the payer) and N DEBIT entries (one per member) per spec §3.
     */
    private List<LedgerEntry> writeLedgerEntries(ExpenseTransaction txn,
                                                  Group group,
                                                  User payer,
                                                  List<SplitAllocation> allocations) {
        List<LedgerEntry> saved = new ArrayList<>();

        // 1. CREDIT the payer for the full amount they paid
        saved.add(ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .transaction(txn)
                        .group(group)
                        .accountUser(payer)
                        .entryType(EntryType.CREDIT)
                        .amount(txn.getTotalAmount())
                        .build()
        ));

        // 2. DEBIT each participant for their share (including the payer for their own share)
        for (SplitAllocation alloc : allocations) {
            saved.add(ledgerEntryRepository.save(
                    LedgerEntry.builder()
                            .transaction(txn)
                            .group(group)
                            .accountUser(alloc.user())
                            .entryType(EntryType.DEBIT)
                            .amount(alloc.amount())
                            .build()
            ));
        }

        return saved;
    }

    // ── Split computation ─────────────────────────────────────────────

    private List<SplitAllocation> computeAllocations(CreateExpenseRequest req,
                                                       Group group,
                                                       SplitType splitType) {
        return switch (splitType) {
            case EQUAL -> computeEqualSplit(req, group);
            case PERCENTAGE -> computePercentageSplit(req);
            case CUSTOM -> computeCustomSplit(req);
        };
    }

    /**
     * EQUAL split: all group members share equally.
     * Uses "largest remainder" to handle penny rounding so the sum is exact.
     */
    private List<SplitAllocation> computeEqualSplit(CreateExpenseRequest req, Group group) {
        List<GroupMember> members = groupMemberRepository.findAllByGroupId(group.getId());
        int n = members.size();
        if (n == 0) throw new BadRequestException("Group has no members");

        BigDecimal total     = req.totalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseShare = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(baseShare.multiply(BigDecimal.valueOf(n)));
        // remainder is always 0 or a small positive amount (at most n-1 cents)
        long centRemainder = remainder.multiply(BigDecimal.valueOf(100)).longValue();

        List<SplitAllocation> result = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            BigDecimal share = (i < centRemainder)
                    ? baseShare.add(new BigDecimal("0.01"))
                    : baseShare;
            result.add(new SplitAllocation(members.get(i).getUser(), share));
        }
        return result;
    }

    /**
     * PERCENTAGE split: each split.value is a percentage (must sum to 100).
     */
    private List<SplitAllocation> computePercentageSplit(CreateExpenseRequest req) {
        if (req.splits() == null || req.splits().isEmpty()) {
            throw new BadRequestException("splits are required for PERCENTAGE split type");
        }
        BigDecimal totalPct = req.splits().stream()
                .map(CreateExpenseRequest.SplitEntry::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPct.compareTo(new BigDecimal("100.00")) != 0) {
            throw new BadRequestException(
                    "Percentage splits must sum to 100, got: " + totalPct.toPlainString());
        }

        BigDecimal total = req.totalAmount().setScale(2, RoundingMode.HALF_UP);
        List<SplitAllocation> result = new ArrayList<>();
        BigDecimal runningSum = BigDecimal.ZERO;
        List<CreateExpenseRequest.SplitEntry> splits = req.splits();

        for (int i = 0; i < splits.size(); i++) {
            CreateExpenseRequest.SplitEntry entry = splits.get(i);
            User user = resolveUser(entry.userId());
            BigDecimal amount;
            if (i == splits.size() - 1) {
                // Last entry: use remainder to avoid rounding drift
                amount = total.subtract(runningSum);
            } else {
                amount = total.multiply(entry.value())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                runningSum = runningSum.add(amount);
            }
            result.add(new SplitAllocation(user, amount));
        }
        return result;
    }

    /**
     * CUSTOM split: each split.value is an absolute amount.
     */
    private List<SplitAllocation> computeCustomSplit(CreateExpenseRequest req) {
        if (req.splits() == null || req.splits().isEmpty()) {
            throw new BadRequestException("splits are required for CUSTOM split type");
        }
        List<SplitAllocation> result = new ArrayList<>();
        for (CreateExpenseRequest.SplitEntry entry : req.splits()) {
            User user = resolveUser(entry.userId());
            result.add(new SplitAllocation(user, entry.value().setScale(2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private User resolveUser(String publicIdStr) {
        return userRepository.findByPublicId(UUID.fromString(publicIdStr))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + publicIdStr));
    }

    private Group findGroup(String publicId) {
        return groupRepository.findByPublicId(UUID.fromString(publicId))
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + publicId));
    }

    private void requireMembership(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
    }

    private ExpenseResponse toExpenseResponse(ExpenseTransaction txn,
                                               List<LedgerEntry> entries,
                                               boolean isReversal) {
        List<LedgerEntryDto> entryDtos = entries.stream()
                .map(e -> new LedgerEntryDto(
                        e.getId(),
                        e.getAccountUser().getPublicId().toString(),
                        e.getEntryType().name(),
                        ExpenseResponse.formatAmount(e.getAmount())
                ))
                .toList();

        return new ExpenseResponse(
                txn.getPublicId().toString(),
                txn.getGroup().getPublicId().toString(),
                txn.getPaidBy().getPublicId().toString(),
                txn.getDescription(),
                ExpenseResponse.formatAmount(txn.getTotalAmount()),
                txn.getCurrency(),
                txn.getSplitType().name(),
                isReversal,
                txn.getCreatedAt(),
                entryDtos
        );
    }

    /** Internal value object: a user and the amount they owe in a split. */
    private record SplitAllocation(User user, BigDecimal amount) {}
}
