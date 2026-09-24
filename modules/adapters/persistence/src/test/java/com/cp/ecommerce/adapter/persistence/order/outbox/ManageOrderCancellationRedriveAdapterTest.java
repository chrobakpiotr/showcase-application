package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockEntity;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockRepository;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.OrderCancellationRedriveConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.TooManyMethods")
class ManageOrderCancellationRedriveAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String COMMAND_ID = "cmd-1";
    private static final String INCIDENT = "incident";

    @Mock
    private IdempotencyLockRepository idempotencyLockRepository;

    @Mock
    private OrderCancellationRedriveCommandEntityRepository commandRepository;

    @Mock
    private OutboxEventEntityRepository outboxRepository;

    @Mock
    private OrderEntityRepository orderRepository;

    private ManageOrderCancellationRedriveAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManageOrderCancellationRedriveAdapter(
                idempotencyLockRepository,
                commandRepository,
                outboxRepository,
                orderRepository);
        given(idempotencyLockRepository.findById(any())).willReturn(Optional.of(mock(IdempotencyLockEntity.class)));
    }

    @Test
    void shouldAuditAndRequeueEligibleCancellationManualReview() {

        final OrderCancellationRedriveCommand command = command("incident-456");
        final OutboxEventEntity event = eligibleEvent();
        final OrderEntity order = cancelledOrder();

        given(commandRepository.findById(COMMAND_ID)).willReturn(Optional.empty());
        given(outboxRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(order);

        assertThat(adapter.redrive(command, NOW)).isEqualTo(OrderCancellationRedriveOutcome.REQUEUED);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(event.getCancellationAttempts()).isZero();
        assertThat(event.getCancellationNextAttemptDate()).isEqualTo(NOW);
        assertThat(event.getCancellationClaimId()).isNull();
        assertThat(event.getCancellationClaimUntil()).isNull();
        assertThat(event.getCancellationLastError()).isNull();

        verify(commandRepository).saveAndFlush(any(OrderCancellationRedriveCommandEntity.class));
        verify(outboxRepository).saveAndFlush(event);
    }

    @Test
    void shouldReplayIdenticalCommandWithoutMutatingSaga() {

        final OrderCancellationRedriveCommand command = command("incident-456");
        given(commandRepository.findById(COMMAND_ID)).willReturn(Optional.of(audit("incident-456")));

        assertThat(adapter.redrive(command, NOW)).isEqualTo(OrderCancellationRedriveOutcome.REPLAYED);

        verify(outboxRepository, never()).findByOrderNumberForUpdate(any());
        verify(commandRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectCommandIdBoundToDifferentPayload() {

        given(commandRepository.findById(COMMAND_ID)).willReturn(Optional.of(audit("original-reason")));

        assertThatThrownBy(() -> adapter.redrive(command("different-reason"), NOW))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("different command data");
    }

    @Test
    void shouldRejectMissingSaga() {

        given(commandRepository.findById(COMMAND_ID)).willReturn(Optional.empty());
        given(outboxRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW)).isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining("saga not found");
    }

    @Test
    void shouldRejectNonManualReviewSaga() {

        final OutboxEventEntity event = eligibleEvent();
        event.setStatus(OutboxEventStatus.CANCELLING);
        stubNewCommand(event);

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("not parked in MANUAL_REVIEW");
    }

    @Test
    void shouldRejectManualReviewThatIsNotCancellationSpecific() {

        final OutboxEventEntity event = eligibleEvent();
        event.setCancellationLastError(null);
        stubNewCommand(event);

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("not a cancellation-recovery incident");
    }

    @Test
    void shouldRejectAnyOutstandingOwnershipMarker() {

        final OutboxEventEntity event = eligibleEvent();
        event.setCancellationClaimUntil(NOW.plusSeconds(30));
        stubNewCommand(event);

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("ownership marker");
    }

    @Test
    void shouldRejectMissingOrder() {

        final OutboxEventEntity event = eligibleEvent();
        stubNewCommand(event);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW)).isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void shouldRejectOrderThatIsNotCancelled() {

        final OutboxEventEntity event = eligibleEvent();
        final OrderEntity order = cancelledOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        stubNewCommand(event);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(order);

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW))
                .isInstanceOf(OrderCancellationRedriveConflictException.class)
                .hasMessageContaining("requires CANCELLED order state");
    }

    @Test
    void shouldFailClosedWhenCommandLockStripeIsMissing() {

        given(idempotencyLockRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.redrive(command(INCIDENT), NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing idempotency lock stripe");
    }

    private void stubNewCommand(final OutboxEventEntity event) {
        given(commandRepository.findById(COMMAND_ID)).willReturn(Optional.empty());
        given(outboxRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
    }

    private static OrderCancellationRedriveCommand command(final String reason) {
        return new OrderCancellationRedriveCommand(COMMAND_ID, ORDER_NUMBER, "operator-a", reason);
    }

    private static OrderCancellationRedriveCommandEntity audit(final String reason) {
        return OrderCancellationRedriveCommandEntity.builder()
                .commandId(COMMAND_ID)
                .orderNumber(ORDER_NUMBER)
                .actor("operator-a")
                .reason(reason)
                .previousError("old error")
                .status(OrderCancellationRedriveOutcome.REQUEUED)
                .createdDate(NOW)
                .build();
    }

    private static OutboxEventEntity eligibleEvent() {
        return OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .status(OutboxEventStatus.MANUAL_REVIEW)
                .createdDate(NOW.minusSeconds(60))
                .nextAttemptDate(NOW.minusSeconds(60))
                .cancellationAttempts(10)
                .cancellationLastError("provider outcome unknown")
                .build();
    }

    private static OrderEntity cancelledOrder() {
        return OrderEntity.builder().id(1L).orderNumber(ORDER_NUMBER).status(OrderStatus.CANCELLED).build();
    }
}
