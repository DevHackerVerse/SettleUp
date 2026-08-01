package com.settleup.worker;

import com.settleup.repository.UserRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RabbitMQ consumer for notification dispatch (spec §7 + Phase 3).
 *
 * Consumes messages from {@code notification.dispatch.queue}.
 *
 * In a production system this would call the FCM HTTP API to push a notification
 * to the user's device using their stored fcm_token.
 *
 * Phase 3 implementation: simulates FCM dispatch by logging the push payload.
 * The infrastructure (queue listener, user lookup, token resolution) is production-ready;
 * swapping the log statement for a real FCM HTTP call is the only production change needed.
 *
 * Message format (from NotificationService):
 * {
 *   "userId":  "uuid",
 *   "type":    "SETTLEMENT_COMPLETE",
 *   "payload": { ... }
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {

    private final UserRepository userRepository;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.notification-dispatch}",
            ackMode = "MANUAL"
    )
    public void dispatchNotification(Map<String, Object> message,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        String userPublicIdStr = (String) message.get("userId");
        String type            = (String) message.get("type");
        Object payload         = message.get("payload");

        log.info("NotificationWorker received: type={} userId={}", type, userPublicIdStr);

        try {
            // Resolve user's FCM token
            String fcmToken = Optional.ofNullable(userPublicIdStr)
                    .flatMap(id -> userRepository.findByPublicId(UUID.fromString(id)))
                    .map(u -> u.getFcmToken())
                    .orElse(null);

            if (fcmToken == null || fcmToken.isBlank()) {
                log.debug("No FCM token for userId={} — skipping push notification", userPublicIdStr);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // ── FCM push (simulated in Phase 3) ──────────────────────
            // In production: replace this with an HTTP call to FCM API
            //   POST https://fcm.googleapis.com/fcm/send
            //   with the token and notification payload.
            sendFcmPush(fcmToken, type, payload);

            channel.basicAck(deliveryTag, false);
            log.info("FCM push dispatched: type={} userId={}", type, userPublicIdStr);

        } catch (Exception e) {
            log.error("Notification dispatch failed: type={} userId={}: {}",
                    type, userPublicIdStr, e.getMessage(), e);
            // NACK without requeue so a bad message doesn't loop forever
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Simulated FCM push.
     *
     * Phase 3: logs the push payload.
     * Production: replace with FCM Admin SDK or HTTP v1 API call.
     *
     * @param fcmToken device FCM registration token
     * @param type     notification type (e.g. SETTLEMENT_COMPLETE)
     * @param payload  notification data payload
     */
    private void sendFcmPush(String fcmToken, String type, Object payload) {
        // TODO (production): integrate Google FCM Admin SDK
        // FirebaseMessaging.getInstance().send(
        //     Message.builder()
        //         .setToken(fcmToken)
        //         .putData("type", type)
        //         .putData("payload", payload.toString())
        //         .build());
        log.info("[FCM SIMULATED] token={} type={} payload={}",
                fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...",
                type, payload);
    }
}
