package com.settleup.service;

import com.settleup.entity.Notification;
import com.settleup.entity.Notification.NotificationType;
import com.settleup.entity.User;
import com.settleup.repository.NotificationRepository;
import com.settleup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Creates in-app notification records and publishes notification events to RabbitMQ
 * for the NotificationWorker to dispatch as FCM push notifications (Phase 3).
 *
 * In-app notifications are persisted to the {@code notifications} table and
 * served by {@link com.settleup.controller.NotificationController}.
 *
 * FCM dispatch is handled asynchronously by the NotificationWorker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchanges.settleup}")
    private String exchange;

    @Value("${app.rabbitmq.routing-keys.notification}")
    private String notificationRoutingKey;

    /**
     * Persists an in-app notification and publishes a dispatch event to RabbitMQ.
     *
     * @param userId      recipient user internal ID
     * @param type        notification type
     * @param payload     JSON payload map (flexible per type)
     */
    @Transactional
    public void send(Long userId, NotificationType type, Map<String, Object> payload) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("NotificationService.send: user not found id={}", userId);
            return;
        }

        // 1. Persist in-app notification
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .payloadJson(payload)
                .read(false)
                .build();
        notificationRepository.save(notification);

        // 2. Publish dispatch event to RabbitMQ for FCM push
        Map<String, Object> queueMessage = Map.of(
                "userId", user.getPublicId().toString(),
                "type", type.name(),
                "payload", payload
        );
        try {
            rabbitTemplate.convertAndSend(exchange, notificationRoutingKey, queueMessage);
        } catch (Exception e) {
            // Queue publish failure should not break the notification save
            log.warn("Failed to publish notification to queue for userId={}: {}", userId, e.getMessage());
        }

        log.debug("Notification created: type={} userId={}", type, userId);
    }

    /**
     * Convenience method for EXPENSE_ADDED notifications to all group members.
     */
    @Transactional
    public void notifyExpenseAdded(Iterable<User> groupMembers,
                                    String groupId,
                                    String transactionId,
                                    String description,
                                    String amount,
                                    String paidByName) {
        for (User member : groupMembers) {
            Map<String, Object> payload = Map.of(
                    "groupId", groupId,
                    "transactionId", transactionId,
                    "description", description,
                    "amount", amount,
                    "paidBy", paidByName
            );
            send(member.getId(), NotificationType.EXPENSE_ADDED, payload);
        }
    }

    /**
     * Convenience method for SETTLEMENT_COMPLETE notification to payer.
     */
    @Transactional
    public void notifySettlementComplete(Long payerUserId,
                                          String settlementId,
                                          String amount,
                                          String payeeName) {
        Map<String, Object> payload = Map.of(
                "settlementId", settlementId,
                "amount", amount,
                "payee", payeeName
        );
        send(payerUserId, NotificationType.SETTLEMENT_COMPLETE, payload);
    }
}
