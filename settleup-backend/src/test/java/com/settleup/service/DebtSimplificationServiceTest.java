package com.settleup.service;

import com.settleup.dto.SimplifiedDebtResponse;
import com.settleup.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DebtSimplificationService}.
 *
 * Four scenarios required by spec §8:
 *  1. Equal splits
 *  2. Uneven splits
 *  3. One person owes everyone
 *  4. Already-zero balances (empty result)
 *
 * Each test builds a balance map directly (mimicking LedgerService output)
 * and verifies the simplification result. No DB or Spring context needed.
 */
class DebtSimplificationServiceTest {

    private DebtSimplificationService service;

    @BeforeEach
    void setUp() {
        service = new DebtSimplificationService();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static User makeUser(String name, String publicId) {
        User u = new User();
        u.setName(name);
        u.setPublicId(UUID.fromString(publicId));
        return u;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /**
     * Asserts that the settlement suggestions fully zero-out the given balance map.
     * Each suggestion "from → to : amount" reduces from's net by amount and
     * increases to's net by amount.
     */
    private void assertBalancesSettled(Map<User, BigDecimal> original,
                                        List<SimplifiedDebtResponse.SettlementSuggestion> suggestions) {
        Map<String, BigDecimal> running = new HashMap<>();
        original.forEach((u, b) -> running.put(u.getPublicId().toString(), b));

        for (var s : suggestions) {
            BigDecimal amt = new BigDecimal(s.amount());
            running.merge(s.fromUserId(), amt,         BigDecimal::add);  // debtor pays → less negative
            running.merge(s.toUserId(),   amt.negate(), BigDecimal::add); // creditor receives → less positive
        }

        // After all settlements, every balance should be zero (within rounding tolerance)
        running.forEach((uid, balance) ->
                assertThat(balance.abs())
                        .as("Balance for %s should be zero after settlements", uid)
                        .isLessThanOrEqualTo(bd("0.01"))
        );
    }

    // ── Test 1: Equal splits ──────────────────────────────────────────

    @Test
    @DisplayName("Equal 3-way split: Karan paid, Aman and Riya each owe 400")
    void equalSplit_producesMinimumTransactions() {
        User karan = makeUser("Karan", "00000000-0000-0000-0000-000000000001");
        User aman  = makeUser("Aman",  "00000000-0000-0000-0000-000000000002");
        User riya  = makeUser("Riya",  "00000000-0000-0000-0000-000000000003");

        // Karan net = +800, Aman = -400, Riya = -400
        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(karan, bd("800.00"));
        balances.put(aman,  bd("-400.00"));
        balances.put(riya,  bd("-400.00"));

        SimplifiedDebtResponse result = service.simplify(balances);
        List<SimplifiedDebtResponse.SettlementSuggestion> suggestions = result.settlementsSuggested();

        // Minimum transactions: 2 (Aman→Karan, Riya→Karan)
        assertThat(suggestions).hasSize(2);
        assertBalancesSettled(balances, suggestions);

        // Verify amounts
        suggestions.forEach(s ->
                assertThat(new BigDecimal(s.amount()))
                        .isEqualByComparingTo(bd("400.00")));
    }

    // ── Test 2: Uneven splits ─────────────────────────────────────────

    @Test
    @DisplayName("Spec example: Karan pays 1200, 4-way equal → Karan +900, others -300")
    void unevenSplit_specExample() {
        User karan = makeUser("Karan", "00000000-0000-0000-0000-000000000001");
        User aman  = makeUser("Aman",  "00000000-0000-0000-0000-000000000002");
        User riya  = makeUser("Riya",  "00000000-0000-0000-0000-000000000003");
        User sam   = makeUser("Sam",   "00000000-0000-0000-0000-000000000004");

        // Karan net = CREDIT 1200 - DEBIT 300 = +900
        // Others net = -300 each
        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(karan, bd("900.00"));
        balances.put(aman,  bd("-300.00"));
        balances.put(riya,  bd("-300.00"));
        balances.put(sam,   bd("-300.00"));

        SimplifiedDebtResponse result = service.simplify(balances);
        List<SimplifiedDebtResponse.SettlementSuggestion> suggestions = result.settlementsSuggested();

        // 3 transfers: each of Aman, Riya, Sam → Karan 300
        assertThat(suggestions).hasSize(3);
        assertBalancesSettled(balances, suggestions);

        suggestions.forEach(s -> {
            assertThat(s.toUserId()).isEqualTo(karan.getPublicId().toString());
            assertThat(new BigDecimal(s.amount())).isEqualByComparingTo(bd("300.00"));
        });
    }

    // ── Test 3: One person owes everyone ──────────────────────────────

    @Test
    @DisplayName("Sam owes everyone: simplification still settles all debts")
    void onePersonOwesEveryone() {
        User alice = makeUser("Alice", "00000000-0000-0000-0000-000000000001");
        User bob   = makeUser("Bob",   "00000000-0000-0000-0000-000000000002");
        User carol = makeUser("Carol", "00000000-0000-0000-0000-000000000003");
        User sam   = makeUser("Sam",   "00000000-0000-0000-0000-000000000004");

        // Sam owes 500 total: Alice +200, Bob +150, Carol +150, Sam -500
        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, bd("200.00"));
        balances.put(bob,   bd("150.00"));
        balances.put(carol, bd("150.00"));
        balances.put(sam,   bd("-500.00"));

        SimplifiedDebtResponse result = service.simplify(balances);
        List<SimplifiedDebtResponse.SettlementSuggestion> suggestions = result.settlementsSuggested();

        // All settlements should go FROM sam
        assertThat(suggestions).isNotEmpty();
        suggestions.forEach(s ->
                assertThat(s.fromUserId())
                        .as("All debts should be from Sam")
                        .isEqualTo(sam.getPublicId().toString()));
        assertBalancesSettled(balances, suggestions);
    }

    // ── Test 4: Already-zero balances ─────────────────────────────────

    @Test
    @DisplayName("All balances already zero → empty suggestion list")
    void alreadyZeroBalances_returnsEmptyList() {
        User alice = makeUser("Alice", "00000000-0000-0000-0000-000000000001");
        User bob   = makeUser("Bob",   "00000000-0000-0000-0000-000000000002");

        // Both zeroed out (already settled)
        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, bd("0.00"));
        balances.put(bob,   bd("0.00"));

        SimplifiedDebtResponse result = service.simplify(balances);

        assertThat(result.settlementsSuggested()).isEmpty();
    }

    // ── Bonus: Empty group ─────────────────────────────────────────────

    @Test
    @DisplayName("Empty balance map → empty suggestion list")
    void emptyGroup_returnsEmptyList() {
        SimplifiedDebtResponse result = service.simplify(Collections.emptyMap());
        assertThat(result.settlementsSuggested()).isEmpty();
    }

    // ── Bonus: Two parties, asymmetric ────────────────────────────────

    @Test
    @DisplayName("Two members with matching balances → single transfer")
    void twoMembers_singleTransfer() {
        User alice = makeUser("Alice", "00000000-0000-0000-0000-000000000001");
        User bob   = makeUser("Bob",   "00000000-0000-0000-0000-000000000002");

        Map<User, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(alice, bd("500.00"));
        balances.put(bob,   bd("-500.00"));

        SimplifiedDebtResponse result = service.simplify(balances);
        List<SimplifiedDebtResponse.SettlementSuggestion> suggestions = result.settlementsSuggested();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).fromUserId()).isEqualTo(bob.getPublicId().toString());
        assertThat(suggestions.get(0).toUserId()).isEqualTo(alice.getPublicId().toString());
        assertThat(new BigDecimal(suggestions.get(0).amount())).isEqualByComparingTo(bd("500.00"));
    }
}
