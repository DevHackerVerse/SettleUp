package com.settleup.service;

import com.settleup.dto.BalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Broadcasts real-time WebSocket events to connected clients (Phase 2, spec §6).
 *
 * Topics pushed:
 *   /topic/group/{groupId}/balances  — updated balances after expense create / settlement
 *   /topic/group/{groupId}/expenses  — new expense notification
 *
 * Message format (spec §6):
 * {
 *   "eventType": "EXPENSE_ADDED",
 *   "groupId": "uuid",
 *   "transactionId": "uuid",
 *   "updatedBalances": [ { "userId": "uuid", "netBalance": "900.00" } ]
 * }
 *
 * All sends are fire-and-forget; errors are logged but never thrown
 * (WebSocket must never block or fail the HTTP response path).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcasts an EXPENSE_ADDED event with updated balances to the group topic.
     *
     * @param groupPublicId  group UUID string
     * @param transactionId  new expense transaction UUID string
     * @param balances       freshly computed balances to include in the push
     */
    public void broadcastExpenseAdded(String groupPublicId,
                                       String transactionId,
                                       BalanceResponse balances) {
        Map<String, Object> payload = buildPayload(
                "EXPENSE_ADDED", groupPublicId, transactionId, balances);
        sendToTopic(groupPublicId, payload);
    }

    /**
     * Broadcasts a SETTLEMENT_COMPLETED event (called by SettlementWorker in Phase 3).
     *
     * @param groupPublicId  group UUID string
     * @param settlementId   settlement UUID string
     * @param balances       freshly computed balances after settlement
     */
    public void broadcastSettlementCompleted(String groupPublicId,
                                              String settlementId,
                                              BalanceResponse balances) {
        Map<String, Object> payload = buildPayload(
                "SETTLEMENT_COMPLETED", groupPublicId, settlementId, balances);
        sendToTopic(groupPublicId, payload);
    }

    // ── Internal ──────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(String eventType,
                                              String groupId,
                                              String entityId,
                                              BalanceResponse balances) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("groupId", groupId);
        payload.put("transactionId", entityId);  // spec uses transactionId field name
        payload.put("updatedBalances", balances.balances().stream()
                .map(b -> Map.of(
                        "userId", b.userId(),
                        "name", b.name(),
                        "netBalance", b.netBalance()
                ))
                .toList());
        return payload;
    }

    private void sendToTopic(String groupPublicId, Map<String, Object> payload) {
        String destination = "/topic/group/" + groupPublicId + "/balances";
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("WS push → {} eventType={}", destination, payload.get("eventType"));
        } catch (Exception e) {
            // WebSocket errors must never break the HTTP write path
            log.warn("WebSocket broadcast failed for groupId={}: {}", groupPublicId, e.getMessage());
        }
    }
}
