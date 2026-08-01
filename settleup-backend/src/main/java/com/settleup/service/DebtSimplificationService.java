package com.settleup.service;

import com.settleup.dto.SimplifiedDebtResponse;
import com.settleup.dto.SimplifiedDebtResponse.SettlementSuggestion;
import com.settleup.entity.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Implements the greedy min-cash-flow debt simplification algorithm (spec §8).
 *
 * Algorithm:
 *  1. Compute net balance per user (positive = owed, negative = owes).
 *  2. Put creditors (positive) in a max-heap, debtors (negative) in a max-heap by abs value.
 *  3. Repeatedly pair largest debtor with largest creditor, settle min(debt, credit),
 *     push remainder back, record a settlement suggestion.
 *  4. Repeat until all heaps are empty.
 *
 * This minimises the number of transactions required to settle the group.
 *
 * This class is a pure-function service with NO side effects — it only reads balances
 * and computes suggestions. Actual settlements go through SettlementService.
 */
@Service
public class DebtSimplificationService {

    private static final BigDecimal EPSILON = new BigDecimal("0.005");

    /**
     * Produces simplified settlement suggestions for a group.
     *
     * @param balances map of User → net balance (positive = owed, negative = owes)
     *                 (typically provided by LedgerService.computeRawBalances)
     * @return list of suggested one-way payments that fully settle all debts
     */
    public SimplifiedDebtResponse simplify(Map<User, BigDecimal> balances) {
        // Max-heap for creditors: sorted by balance descending
        PriorityQueue<UserBalance> creditors = new PriorityQueue<>(
                Comparator.comparing(UserBalance::amount).reversed());

        // Max-heap for debtors: sorted by absolute debt descending
        PriorityQueue<UserBalance> debtors = new PriorityQueue<>(
                Comparator.comparing(UserBalance::amount).reversed());

        for (Map.Entry<User, BigDecimal> entry : balances.entrySet()) {
            BigDecimal net = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new UserBalance(entry.getKey(), net));
            } else if (net.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new UserBalance(entry.getKey(), net.negate())); // store as positive
            }
            // zero balances are ignored
        }

        List<SettlementSuggestion> suggestions = new ArrayList<>();

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            UserBalance debtor   = debtors.poll();
            UserBalance creditor = creditors.poll();

            BigDecimal settle = debtor.amount().min(creditor.amount());

            suggestions.add(new SettlementSuggestion(
                    debtor.user().getPublicId().toString(),
                    debtor.user().getName(),
                    creditor.user().getPublicId().toString(),
                    creditor.user().getName(),
                    settle.setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));

            BigDecimal debtorRemainder   = debtor.amount().subtract(settle);
            BigDecimal creditorRemainder = creditor.amount().subtract(settle);

            // Push remainder back if above dust threshold
            if (debtorRemainder.compareTo(EPSILON) > 0) {
                debtors.add(new UserBalance(debtor.user(), debtorRemainder));
            }
            if (creditorRemainder.compareTo(EPSILON) > 0) {
                creditors.add(new UserBalance(creditor.user(), creditorRemainder));
            }
        }

        return new SimplifiedDebtResponse(suggestions);
    }

    /** Internal value object pairing a User with their (positive) balance amount. */
    private record UserBalance(User user, BigDecimal amount) {}
}
