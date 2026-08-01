package com.settleup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity mapping to the {@code ledger_entries} table.
 *
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  LEDGER RULE — READ THIS BEFORE TOUCHING THIS CLASS         ║
 * ║  This entity is APPEND-ONLY.                                ║
 * ║  No UPDATE. No DELETE. Ever.                                ║
 * ║  Enforced by:                                               ║
 * ║    1. @Immutable on the entity (Hibernate won't dirty-check)║
 * ║    2. Repository interface has no save()/delete() methods   ║
 * ║       that take an existing entity (append-only repo)       ║
 * ║    3. Application service layer never calls update/delete   ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Balance formula: SUM(CREDIT amount) − SUM(DEBIT amount) per user/group.
 *   Positive = others owe this user.
 *   Negative = this user owes others.
 *
 * amount is always > 0 (sign is captured by entry_type).
 */
@Entity
@Table(name = "ledger_entries")
@Immutable   // Hibernate: skip dirty checking — this entity is never updated
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private ExpenseTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_user_id", nullable = false)
    private User accountUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    /** Always positive. Sign is captured by entryType. Never float/double. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // No @Setter — combined with @Immutable prevents accidental mutation

    public enum EntryType {
        DEBIT, CREDIT
    }
}
