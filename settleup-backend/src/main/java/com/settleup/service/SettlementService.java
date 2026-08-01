package com.settleup.service;

import com.settleup.dto.CreateSettlementRequest;
import com.settleup.dto.SettlementResponse;
import com.settleup.entity.Settlement;
import com.settleup.entity.Settlement.SettlementStatus;
import com.settleup.entity.User;
import com.settleup.exception.BadRequestException;
import com.settleup.exception.ResourceNotFoundException;
import com.settleup.repository.GroupRepository;
import com.settleup.repository.SettlementRepository;
import com.settleup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Handles settlement creation and status queries (spec §5.4).
 *
 * Settlement is an ASYNC flow:
 *   1. API creates a Settlement record in DB with status=PENDING.
 *   2. API publishes a message to settlement.process.queue.
 *   3. API returns 202 immediately (non-blocking).
 *   4. SettlementWorker picks up the message, processes it, and transitions
 *      the status to COMPLETED (or FAILED on error).
 *
 * Idempotency: if a client sends the same idempotencyKey twice,
 * the second call returns the existing settlement (no duplicate processing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchanges.settleup}")
    private String exchange;

    @Value("${app.rabbitmq.routing-keys.settlement}")
    private String settlementRoutingKey;

    // ── Create ────────────────────────────────────────────────────────

    /**
     * Creates a settlement and enqueues it for async processing.
     * Returns 202 immediately; status transitions happen in the worker.
     *
     * Idempotency: if idempotencyKey already exists, returns existing settlement.
     */
    @Transactional
    public SettlementResponse initiate(String groupPublicId,
                                        CreateSettlementRequest req,
                                        User currentUser) {
        var group = groupService.findGroupByPublicId(groupPublicId);
        groupService.requireMembership(group.getId(), currentUser.getId());

        UUID idempotencyKey = UUID.fromString(req.idempotencyKey());

        // ── Idempotency check ─────────────────────────────────────────
        var existing = settlementRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent settlement request — returning existing: key={}", idempotencyKey);
            return toResponse(existing.get());
        }

        // Resolve payee
        User payee = userRepository.findByPublicId(UUID.fromString(req.payeeId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payee not found: " + req.payeeId()));
        groupService.requireMembership(group.getId(), payee.getId());

        if (currentUser.getId().equals(payee.getId())) {
            throw new BadRequestException("Payer and payee must be different users");
        }

        // Persist PENDING settlement
        Settlement settlement = Settlement.builder()
                .group(group)
                .payer(currentUser)
                .payee(payee)
                .amount(req.amount().setScale(2, RoundingMode.HALF_UP))
                .status(SettlementStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
        settlement = settlementRepository.save(settlement);

        // Publish to RabbitMQ queue (non-blocking; worker does the actual processing)
        Map<String, String> message = Map.of(
                "settlementId", settlement.getPublicId().toString(),
                "idempotencyKey", idempotencyKey.toString()
        );
        rabbitTemplate.convertAndSend(exchange, settlementRoutingKey, message);

        log.info("Settlement queued: settlementId={} groupId={} amount={}",
                settlement.getPublicId(), group.getPublicId(), req.amount());

        return toResponse(settlement);
    }

    // ── Get by ID ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(String settlementPublicId, User currentUser) {
        Settlement s = settlementRepository.findByPublicId(UUID.fromString(settlementPublicId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement not found: " + settlementPublicId));

        // Only payer or payee can view the settlement
        boolean isInvolved = s.getPayer().getId().equals(currentUser.getId())
                || s.getPayee().getId().equals(currentUser.getId());
        if (!isInvolved) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not involved in this settlement");
        }

        return toResponse(s);
    }

    // ── Mapper ────────────────────────────────────────────────────────

    public static SettlementResponse toResponse(Settlement s) {
        return new SettlementResponse(
                s.getPublicId().toString(),
                s.getGroup().getPublicId().toString(),
                s.getPayer().getPublicId().toString(),
                s.getPayee().getPublicId().toString(),
                s.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                s.getStatus().name(),
                s.getIdempotencyKey().toString(),
                s.getMockUpiRef(),
                s.getCreatedAt(),
                s.getCompletedAt()
        );
    }
}
