package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockEntity;
import com.cp.ecommerce.adapter.persistence.order.idempotency.IdempotencyLockRepository;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedriveDispatchAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final String COMMAND_ID = "cmd";
    private static final String DISPATCH_ID = "dispatch";
    private static final String OPERATOR_ID = "operator";
    private static final String REASON = "inspected";
    private final IdempotencyLockRepository locks = mock(IdempotencyLockRepository.class);
    private final DispatchRedriveAuditRepository audits = mock(DispatchRedriveAuditRepository.class);
    private final OrderPlacementDispatchEntityRepository rows = mock(OrderPlacementDispatchEntityRepository.class);
    private final RedriveDispatchAdapter adapter = new RedriveDispatchAdapter(locks, audits, rows);
    private final DispatchRedriveCommand command = new DispatchRedriveCommand(
            COMMAND_ID,
            DISPATCH_ID,
            OPERATOR_ID,
            " inspected ");

    private void stripe() {
        when(locks.findById(anyInt())).thenReturn(Optional.of(mock(IdempotencyLockEntity.class)));
    }

    @Test
    void shouldFailClosedWhenIdempotencyLockStripeIsMissing() {
        when(locks.findById(anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.redrive(command, NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing idempotency lock stripe for dispatch redrive");
        verify(locks).findById(anyInt());
        verifyNoInteractions(audits, rows);
    }

    @Test
    void shouldAuditPriorAttemptsAndResetOnlySameStableRow() {
        stripe();
        final var row = row(OrderPlacementDispatchStatus.PARKED, "ATTEMPT_BUDGET_EXHAUSTED");
        when(rows.findByIdForUpdate(DISPATCH_ID)).thenReturn(Optional.of(row));
        assertThat(adapter.redrive(command, NOW)).isEqualTo(DispatchRedriveOutcome.REQUEUED);
        final var snapshot = ArgumentCaptor.forClass(DispatchRedriveAuditEntity.class);
        verify(audits).saveAndFlush(snapshot.capture());
        assertThat(snapshot.getValue().getPreviousAttempts()).isEqualTo(8);
        assertThat(snapshot.getValue().getPreviousReason()).isEqualTo("ATTEMPT_BUDGET_EXHAUSTED");
        assertThat(snapshot.getValue().getActor()).isEqualTo(OPERATOR_ID);
        assertThat(snapshot.getValue().getOriginalCreatedAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(row.getDispatchId()).isEqualTo(DISPATCH_ID);
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PENDING);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextAttemptDate()).isEqualTo(NOW);
        assertThat(row.getClaimId()).isNull();
        assertThat(row.getClaimUntil()).isNull();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void shouldReplayOriginalCommandWithoutReadingOrChangingDispatch() {
        stripe();
        when(audits.findById(COMMAND_ID)).thenReturn(
                Optional.of(
                        DispatchRedriveAuditEntity.builder()
                                .commandId(COMMAND_ID)
                                .dispatchId(DISPATCH_ID)
                                .actor(OPERATOR_ID)
                                .reason(REASON)
                                .build()));
        assertThat(adapter.redrive(command, NOW)).isEqualTo(DispatchRedriveOutcome.REPLAYED);
        for (final var changed : java.util.List.of(
                new DispatchRedriveCommand(COMMAND_ID, "other", OPERATOR_ID, REASON),
                new DispatchRedriveCommand(COMMAND_ID, DISPATCH_ID, "other", REASON),
                new DispatchRedriveCommand(COMMAND_ID, DISPATCH_ID, OPERATOR_ID, "other"))) {
            assertThatThrownBy(() -> adapter.redrive(changed, NOW))
                    .isInstanceOf(OrderPlacementDispatchRedriveConflictException.class);
        }
        verifyNoInteractions(rows);
    }

    @Test
    void shouldRejectMissingTargetAndEveryIneligibleStateOrReason() {
        stripe();
        assertThatThrownBy(() -> adapter.redrive(command, NOW)).isInstanceOf(ApplicationNotFoundException.class);
        for (final var status : OrderPlacementDispatchStatus.values()) {
            if (status == OrderPlacementDispatchStatus.PARKED) {
                continue;
            }
            when(rows.findByIdForUpdate(DISPATCH_ID)).thenReturn(Optional.of(row(status, "ATTEMPT_BUDGET_EXHAUSTED")));
            assertThatThrownBy(() -> adapter.redrive(command, NOW))
                    .isInstanceOf(OrderPlacementDispatchRedriveConflictException.class);
        }
        for (final String reason : new String[] { "ORDER_MISSING", "OTHER", "provider secret", null }) {
            when(rows.findByIdForUpdate(DISPATCH_ID)).thenReturn(Optional.of(row(OrderPlacementDispatchStatus.PARKED, reason)));
            assertThatThrownBy(() -> adapter.redrive(command, NOW))
                    .isInstanceOf(OrderPlacementDispatchRedriveConflictException.class);
        }
        verify(audits, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private static OrderPlacementDispatchEntity row(final OrderPlacementDispatchStatus status, final String reason) {
        return OrderPlacementDispatchEntity.builder()
                .dispatchId(DISPATCH_ID)
                .orderNumber("order")
                .dispatchType(OrderPlacementDispatchType.CONFIRMATION_EMAIL)
                .status(status)
                .attempts(8)
                .createdDate(NOW.minusSeconds(100))
                .nextAttemptDate(NOW)
                .lastError(reason)
                .build();
    }
}
