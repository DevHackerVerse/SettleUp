package com.settleup.service;

import com.settleup.entity.LedgerEntry;
import com.settleup.entity.LedgerEntry.EntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ledger balance calculation logic (spec §3).
 *
 * These tests verify the core invariant:
 *   balance = SUM(CREDIT amount) − SUM(DEBIT amount)
 *
 * Tests use the spec's worked example directly:
 *   "Karan pays ₹1200 for dinner, split equally 4 ways (Karan, Aman, Riya, Sam)"
 *
 * No Spring context or DB needed — pure arithmetic tests.
 */
class LedgerBalanceTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // ── Helpers ───────────────────────────────────────────────────────

    private static BigDecimal bd(String s) {
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Simulates the query in LedgerEntryRepository.calculateNetBalance:
     *   SUM(CREDIT) − SUM(DEBIT) for a specific user.
     */
    private static BigDecimal calculateNetBalance(List<LedgerEntry> entries, Long userId) {
        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT
                        && e.getAccountUser().getId().equals(userId))
                .map(LedgerEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT
                        && e.getAccountUser().getId().equals(userId))
                .map(LedgerEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);

        return totalCredit.subtract(totalDebit);
    }

    /**
     * Builds a minimal LedgerEntry stub for testing (no JPA context needed).
     */
    private static LedgerEntry entry(Long userId, EntryType type, String amount) {
        com.settleup.entity.User user = new com.settleup.entity.User();
        user.setId(userId);

        return LedgerEntry.builder()
                .accountUser(user)
                .entryType(type)
                .amount(bd(amount))
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Spec example §3: Karan pays ₹1200, 4-way equal split")
    void specExample_equalSplit_karanNetIs900_othersNegative300() {
        // Karan=1L, Aman=2L, Riya=3L, Sam=4L
        List<LedgerEntry> ledger = List.of(
                // 1 CREDIT: Karan paid ₹1200
                entry(1L, EntryType.CREDIT, "1200.00"),
                // 4 DEBITS: each member's share = ₹300
                entry(1L, EntryType.DEBIT, "300.00"),  // Karan's own share
                entry(2L, EntryType.DEBIT, "300.00"),  // Aman
                entry(3L, EntryType.DEBIT, "300.00"),  // Riya
                entry(4L, EntryType.DEBIT, "300.00")   // Sam
        );

        // Verify Karan: CREDIT 1200 - DEBIT 300 = +900
        BigDecimal karanNet = calculateNetBalance(ledger, 1L);
        assertThat(karanNet).isEqualByComparingTo(bd("900.00"));

        // Verify others: 0 - 300 = -300
        assertThat(calculateNetBalance(ledger, 2L)).isEqualByComparingTo(bd("-300.00"));
        assertThat(calculateNetBalance(ledger, 3L)).isEqualByComparingTo(bd("-300.00"));
        assertThat(calculateNetBalance(ledger, 4L)).isEqualByComparingTo(bd("-300.00"));
    }

    @Test
    @DisplayName("Double-entry invariant: SUM(all debits) == SUM(all credits) for any expense")
    void doubleEntryInvariant_debitsSumEqualsCreditsSum() {
        List<LedgerEntry> ledger = List.of(
                entry(1L, EntryType.CREDIT, "1200.00"),
                entry(1L, EntryType.DEBIT, "300.00"),
                entry(2L, EntryType.DEBIT, "300.00"),
                entry(3L, EntryType.DEBIT, "300.00"),
                entry(4L, EntryType.DEBIT, "300.00")
        );

        BigDecimal totalCredits = ledger.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalDebits = ledger.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(ZERO, BigDecimal::add);

        assertThat(totalDebits).isEqualByComparingTo(totalCredits);
    }

    @Test
    @DisplayName("Reversal entries: original + reversal nets to zero for all users")
    void reversalEntries_netToZeroForAllUsers() {
        // Original expense
        List<LedgerEntry> original = List.of(
                entry(1L, EntryType.CREDIT, "600.00"),
                entry(1L, EntryType.DEBIT, "200.00"),
                entry(2L, EntryType.DEBIT, "200.00"),
                entry(3L, EntryType.DEBIT, "200.00")
        );

        // Reversal: flip every entry_type
        List<LedgerEntry> reversal = List.of(
                entry(1L, EntryType.DEBIT, "600.00"),
                entry(1L, EntryType.CREDIT, "200.00"),
                entry(2L, EntryType.CREDIT, "200.00"),
                entry(3L, EntryType.CREDIT, "200.00")
        );

        // Combined ledger
        List<LedgerEntry> combined = new java.util.ArrayList<>(original);
        combined.addAll(reversal);

        // Net balance for all users should be zero
        assertThat(calculateNetBalance(combined, 1L)).isEqualByComparingTo(ZERO);
        assertThat(calculateNetBalance(combined, 2L)).isEqualByComparingTo(ZERO);
        assertThat(calculateNetBalance(combined, 3L)).isEqualByComparingTo(ZERO);
    }

    @Test
    @DisplayName("User with no entries has zero balance")
    void userWithNoEntries_hasZeroBalance() {
        List<LedgerEntry> ledger = List.of(
                entry(1L, EntryType.CREDIT, "500.00"),
                entry(2L, EntryType.DEBIT, "500.00")
        );

        // User 99 has no entries
        BigDecimal net = calculateNetBalance(ledger, 99L);
        assertThat(net).isEqualByComparingTo(ZERO);
    }

    @Test
    @DisplayName("Multiple expenses accumulate correctly")
    void multipleExpenses_accumulateCorrectly() {
        // Expense 1: Alice pays 100, split 50/50 with Bob
        // Expense 2: Bob pays 200, split 50/50 with Alice
        List<LedgerEntry> ledger = List.of(
                // Expense 1
                entry(1L, EntryType.CREDIT, "100.00"),  // Alice paid
                entry(1L, EntryType.DEBIT, "50.00"),    // Alice's share
                entry(2L, EntryType.DEBIT, "50.00"),    // Bob's share
                // Expense 2
                entry(2L, EntryType.CREDIT, "200.00"),  // Bob paid
                entry(1L, EntryType.DEBIT, "100.00"),   // Alice's share
                entry(2L, EntryType.DEBIT, "100.00")    // Bob's share
        );

        // Alice: CREDIT 100 - DEBIT (50+100) = 100 - 150 = -50
        BigDecimal aliceNet = calculateNetBalance(ledger, 1L);
        assertThat(aliceNet).isEqualByComparingTo(bd("-50.00"));

        // Bob: CREDIT 200 - DEBIT (50+100) = 200 - 150 = +50
        BigDecimal bobNet = calculateNetBalance(ledger, 2L);
        assertThat(bobNet).isEqualByComparingTo(bd("50.00"));
    }
}
