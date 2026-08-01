package com.settleup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code expense_transactions} table.
 *
 * This is the transaction header. The actual debit/credit entries live in
 * {@link LedgerEntry}. Reversal transactions link back via
 * {@code reversedTransaction}.
 *
 * IMPORTANT: This entity may be "deleted" only via a reversal transaction
 * (see spec §3). No hard deletes.
 */
@Entity
@Table(name = "expense_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Column(nullable = false, length = 255)
    private String description;

    /** Always BigDecimal — never float/double. Spec §NFR rule #1. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 12)
    private SplitType splitType;

    /**
     * Non-null when this transaction is a reversal of another.
     * The reversed transaction is linked here for audit traceability.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_transaction_id")
    private ExpenseTransaction reversedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (currency == null) {
            currency = "INR";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum SplitType {
        EQUAL, PERCENTAGE, CUSTOM
    }
}
