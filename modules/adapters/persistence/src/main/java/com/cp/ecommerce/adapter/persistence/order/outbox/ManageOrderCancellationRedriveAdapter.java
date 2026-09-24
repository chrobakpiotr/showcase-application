package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockRepository;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRedriveOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.OrderCancellationRedriveConflictException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Atomically audits an operator command and requeues only a cancellation-specific MANUAL_REVIEW saga.
 *
 * <p>
 * This adapter does not execute cancellation side effects. Existing recovery workers acquire the normal cancellation lease
 * after the row is returned to CANCELLING.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class ManageOrderCancellationRedriveAdapter implements ManageOrderCancellationRedriveOutPort {

    private static final int LOCK_STRIPES = 64;

    private final IdempotencyLockRepository idempotencyLockRepository;
    private final OrderCancellationRedriveCommandEntityRepository commandRepository;
    private final OutboxEventEntityRepository outboxRepository;
    private final OrderEntityRepository orderRepository;

    @Override
    @Transactional
    public OrderCancellationRedriveOutcome redrive(final OrderCancellationRedriveCommand command, final Instant now) {

        lockCommandId(command.commandId());

        final OrderCancellationRedriveCommandEntity existing = commandRepository.findById(command.commandId()).orElse(null);
        if (existing != null) {
            assertSameCommand(existing, command);
            return OrderCancellationRedriveOutcome.REPLAYED;
        }

        final OutboxEventEntity event = outboxRepository.findByOrderNumberForUpdate(command.orderNumber())
                .orElseThrow(
                        () -> new ApplicationNotFoundException("Order cancellation saga not found: " + command.orderNumber()));

        assertEligibleCancellationManualReview(event, command.orderNumber());

        final OrderEntity order = orderRepository.findByOrderNumberForUpdate(command.orderNumber());
        if (order == null) {
            throw new ApplicationNotFoundException("Order not found: " + command.orderNumber());
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new OrderCancellationRedriveConflictException(
                    "Cancellation manual-review redrive requires CANCELLED order state: " + command.orderNumber() + " -> "
                            + order.getStatus());
        }

        commandRepository.saveAndFlush(
                OrderCancellationRedriveCommandEntity.builder()
                        .commandId(command.commandId())
                        .orderNumber(command.orderNumber())
                        .actor(command.actor())
                        .reason(command.reason())
                        .previousError(event.getCancellationLastError())
                        .status(OrderCancellationRedriveOutcome.REQUEUED)
                        .createdDate(now)
                        .build());

        event.setStatus(OutboxEventStatus.CANCELLING);
        event.setCancellationAttempts(0);
        event.setCancellationNextAttemptDate(now);
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        event.setCancellationLastError(null);
        outboxRepository.saveAndFlush(event);

        return OrderCancellationRedriveOutcome.REQUEUED;
    }

    private void lockCommandId(final String commandId) {

        idempotencyLockRepository.findById(Math.floorMod(commandId.hashCode(), LOCK_STRIPES))
                .orElseThrow(() -> new IllegalStateException("Missing idempotency lock stripe for cancellation redrive"));
    }

    private static void assertSameCommand(
            final OrderCancellationRedriveCommandEntity existing,
            final OrderCancellationRedriveCommand command) {

        if (!existing.getOrderNumber().equals(command.orderNumber()) || !existing.getActor().equals(command.actor())
                || !existing.getReason().equals(command.reason())) {
            throw new OrderCancellationRedriveConflictException(
                    "Redrive commandId '" + command.commandId() + "' is already bound to different command data");
        }
    }

    private static void assertEligibleCancellationManualReview(final OutboxEventEntity event, final String orderNumber) {

        if (event.getStatus() != OutboxEventStatus.MANUAL_REVIEW) {
            throw new OrderCancellationRedriveConflictException("Order '" + orderNumber + "' is not parked in MANUAL_REVIEW");
        }
        if (event.getCancellationLastError() == null || event.getCancellationLastError().isBlank()) {
            throw new OrderCancellationRedriveConflictException(
                    "Order '" + orderNumber + "' MANUAL_REVIEW is not a cancellation-recovery incident");
        }
        if (event.getClaimId() != null || event.getClaimUntil() != null || event.getCancellationClaimId() != null
                || event.getCancellationClaimUntil() != null) {
            throw new OrderCancellationRedriveConflictException(
                    "Order '" + orderNumber + "' still has an active or stale recovery ownership marker");
        }
    }
}
