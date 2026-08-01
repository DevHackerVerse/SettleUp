package com.settleup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code groups} table.
 *
 * "groups" is a reserved keyword in SQL. The table is quoted
 * by the PostgreSQL driver automatically when using the @Table mapping.
 *
 * Budget fields are used in Phase 5 (budget alerts).
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    /** Budget cap for the group (Phase 5). Null means no budget set. */
    @Column(name = "budget_amount", precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    /** Percentage of budget used at which an alert is triggered (Phase 5). */
    @Column(name = "budget_alert_threshold_pct")
    private Short budgetAlertThresholdPct;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (defaultCurrency == null) {
            defaultCurrency = "INR";
        }
        if (budgetAlertThresholdPct == null) {
            budgetAlertThresholdPct = 80;
        }
    }
}
