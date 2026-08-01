package com.settleup.repository;

import com.settleup.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Append-only repository for {@link LedgerEntry}.
 *
 * IMPORTANT: This interface intentionally does NOT expose any method
 * that could mutate existing entries. Only {@code save()} (for new entries)
 * and read methods are present. No {@code delete}, no {@code deleteById},
 * no {@code deleteAll}. The parent JpaRepository does expose these but
 * they must NEVER be called from application code.
 *
 * The @Immutable annotation on the entity itself prevents Hibernate
 * dirty-checking from accidentally updating rows.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Finds all ledger entries for a given group and user.
     * Used when in-memory balance computation is preferred.
     */
    List<LedgerEntry> findByGroupIdAndAccountUserId(Long groupId, Long accountUserId);

    /**
     * Computes the net balance for a specific user in a group via native SQL.
     *
     * Balance = SUM(CREDIT amount) − SUM(DEBIT amount)
     *   Positive = others owe this user.
     *   Negative = this user owes others.
     *
     * Returns 0.00 when there are no entries (COALESCE handles NULLs).
     */
    @Query(value = """
        SELECT
          COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) -
          COALESCE(SUM(CASE WHEN entry_type = 'DEBIT'  THEN amount ELSE 0 END), 0)
        FROM ledger_entries
        WHERE group_id = :groupId
          AND account_user_id = :userId
    """, nativeQuery = true)
    BigDecimal calculateNetBalance(@Param("groupId") Long groupId,
                                   @Param("userId") Long userId);

    /**
     * Returns all entries for a given transaction.
     * Used for reversal validation (ensure original txn is not already reversed).
     */
    List<LedgerEntry> findByTransactionId(Long transactionId);

    /**
     * Returns all distinct user IDs that have ledger entries in a group.
     * Used to enumerate participants when computing balances for the entire group.
     */
    @Query("""
        SELECT DISTINCT le.accountUser.id FROM LedgerEntry le
        WHERE le.group.id = :groupId
    """)
    List<Long> findDistinctUserIdsByGroupId(@Param("groupId") Long groupId);
}
