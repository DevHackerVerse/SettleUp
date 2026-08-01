package com.settleup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity mapping to the {@code notifications} table.
 *
 * payload_json is stored as JSONB in PostgreSQL.
 * We use Hibernate's native JSONB mapping via @JdbcTypeCode(SqlTypes.JSON).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private NotificationType type;

    /**
     * Flexible JSON payload stored in PostgreSQL JSONB column.
     * Hibernate 6+ handles JSONB natively via @JdbcTypeCode(SqlTypes.JSON).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        EXPENSE_ADDED, SETTLEMENT_COMPLETE, BUDGET_ALERT, GROUP_INVITE
    }
}
