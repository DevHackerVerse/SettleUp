package com.settleup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code settlements} table.
 *
 * Settlement lifecycle (async via RabbitMQ):
 *   PENDING → PROCESSING → COMPLETED | FAILED
 *
 * idempotency_key is client-generated and unique in the DB.
 * The Settlement Worker must check this key before processing
 * to guard against RabbitMQ redelivery double-processing.
 */
@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    /** Always BigDecimal. Never float/double. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SettlementStatus status;

    /** Client-generated UUID for idempotency. Unique constraint in DB. */
    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "mock_upi_ref", length = 50)
    private String mockUpiRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set when processing completes or fails. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (status == null) {
            status = SettlementStatus.PENDING;
        }
    }

    public enum SettlementStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
