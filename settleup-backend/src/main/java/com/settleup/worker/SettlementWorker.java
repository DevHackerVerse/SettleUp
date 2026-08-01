package com.settleup.worker;

import com.settleup.dto.BalanceResponse;
import com.settleup.entity.LedgerEntry;
import com.settleup.entity.LedgerEntry.EntryType;
import com.settleup.entity.Settlement;
import com.settleup.entity.Settlement.SettlementStatus;
import com.settleup.entity.ExpenseTransaction;
import com.settleup.entity.ExpenseTransaction.SplitType;
import com.settleup.repository.ExpenseTransactionRepository;
import com.settleup.repository.LedgerEntryRepository;
import com.settleup.repository.SettlementRepository;
import com.settleup.service.LedgerService;
import com.settleup.service.NotificationService;
import com.settleup.service.WebSocketEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ consumer that processes settlement requests asynchronously (spec §7 + Phase 3).
 *
 * Processing flow per message:
 *  1. Extract settlementId and idempotencyKey from message.
 *  2. Load the Settlement from DB — verify status is PENDING.
 *  3. IDEMPOTENCY CHECK: if already COMPLETED or FAILED → ACK and skip (no-op).
 *  4. Transition status → PROCESSING.
 *  5. Simulate mock UPI payment with artificial delay (1-2 seconds).
 *  6. Generate mock UPI reference number.
 *  7. Write a settling ledger entry pair (CREDIT payer, DEBIT payee).
 *  8. Transition status → COMPLETED, set completedAt.
 *  9. Invalidate Redis balance cache + WebSocket push.
 * 10. Send SETTLEMENT_COMPLETE notification to payer.
 * 11. ACK the message.
 *
 * On any exception → NACK with requeue=false (goes to DLQ if configured).
 *
 * Spec §11 (idempotency): duplicate messages from RabbitMQ redelivery are
 * caught at step 3 and silently ACKed without double-processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementWorker {

    private final SettlementRepository settlementRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ExpenseTransactionRepository expenseTransactionRepository;
    private final LedgerService ledgerService;
    private final WebSocketEventService webSocketEventService;
    private final NotificationService notificationService;

    @RabbitListener(
            queues = "${app.rabbitmq.queues.settlement-process}",
            ackMode = "MANUAL"
    )
    @Transactional
    public void processSettlement(Map<String, String> message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        String settlementIdStr = message.get("settlementId");
        log.info("SettlementWorker received: settlementId={}", settlementIdStr);

        try {
            UUID settlementPublicId = UUID.fromString(settlementIdStr);
            Settlement settlement = settlementRepository.findByPublicId(settlementPublicId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Settlement not found: " + settlementIdStr));

            // ── Idempotency guard ─────────────────────────────────────
            if (settlement.getStatus() == SettlementStatus.COMPLETED
                    || settlement.getStatus() == SettlementStatus.FAILED) {
                log.info("Duplicate settlement message — already {}: settlementId={}",
                        settlement.getStatus(), settlementIdStr);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // ── Step 1: Mark PROCESSING ───────────────────────────────
            settlement.setStatus(SettlementStatus.PROCESSING);
            settlementRepository.save(settlement);

            // ── Step 2: Simulate UPI gateway delay (1.5 seconds) ──────
            Thread.sleep(1500);

            // ── Step 3: Generate mock UPI reference ───────────────────
            String mockUpiRef = "UPI" + System.currentTimeMillis();

            // ── Step 4: Write settling ledger entries ─────────────────
            // Payer's debt is resolved: write a CREDIT for payer (reduces their negative balance)
            // Payee's credit is reduced: write a DEBIT for payee (reduces their positive balance)
            // We use a special "settlement" expense transaction as the ledger anchor.
            ExpenseTransaction settlementTxn = ExpenseTransaction.builder()
                    .group(settlement.getGroup())
                    .paidBy(settlement.getPayer())
                    .description("SETTLEMENT: " + settlement.getPayer().getName()
                            + " → " + settlement.getPayee().getName())
                    .totalAmount(settlement.getAmount())
                    .currency(settlement.getGroup().getDefaultCurrency())
                    .splitType(SplitType.CUSTOM)
                    .createdBy(settlement.getPayer())
                    .build();
            settlementTxn = expenseTransactionRepository.save(settlementTxn);

            // Payer CREDIT: they paid money out → their debt decreases (net balance goes up)
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(settlementTxn)
                    .group(settlement.getGroup())
                    .accountUser(settlement.getPayer())
                    .entryType(EntryType.CREDIT)
                    .amount(settlement.getAmount())
                    .build());

            // Payee DEBIT: they received money → what they're owed decreases (net balance goes down)
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(settlementTxn)
                    .group(settlement.getGroup())
                    .accountUser(settlement.getPayee())
                    .entryType(EntryType.DEBIT)
                    .amount(settlement.getAmount())
                    .build());

            // ── Step 5: Mark COMPLETED ────────────────────────────────
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setMockUpiRef(mockUpiRef);
            settlement.setCompletedAt(LocalDateTime.now());
            settlementRepository.save(settlement);

            log.info("Settlement COMPLETED: settlementId={} upiRef={}", settlementIdStr, mockUpiRef);

            // ── Step 6: Cache invalidation + WebSocket push ───────────
            Long groupId = settlement.getGroup().getId();
            ledgerService.invalidateBalanceCache(groupId);
            BalanceResponse updatedBalances = ledgerService.computeGroupBalances(groupId);
            webSocketEventService.broadcastSettlementCompleted(
                    settlement.getGroup().getPublicId().toString(),
                    settlement.getPublicId().toString(),
                    updatedBalances);

            // ── Step 7: Notification to payer ─────────────────────────
            notificationService.notifySettlementComplete(
                    settlement.getPayer().getId(),
                    settlement.getPublicId().toString(),
                    settlement.getAmount().toPlainString(),
                    settlement.getPayee().getName()
            );

            // ── ACK ───────────────────────────────────────────────────
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Settlement processing interrupted: settlementId={}", settlementIdStr, e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("Settlement processing failed: settlementId={}", settlementIdStr, e);
            // Mark FAILED in DB if we can identify the settlement
            try {
                UUID settlementPublicId = UUID.fromString(settlementIdStr);
                settlementRepository.findByPublicId(settlementPublicId).ifPresent(s -> {
                    s.setStatus(SettlementStatus.FAILED);
                    s.setCompletedAt(LocalDateTime.now());
                    settlementRepository.save(s);
                });
            } catch (Exception ex) {
                log.error("Could not mark settlement as FAILED: {}", ex.getMessage());
            }
            // NACK without requeue (message goes to DLQ / is discarded)
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
