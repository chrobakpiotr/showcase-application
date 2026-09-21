package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort.CancellationClaim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacementSagaArbitrationAdapterTest {

    private static final String ORDER_NUMBER = "ORDER-1";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private transient OutboxEventEntityRepository repository;

    private transient OrderPlacementSagaArbitrationAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new OrderPlacementSagaArbitrationAdapter(repository, CLOCK);
    }

    @Test
    void shouldReturnNoSagaWhenPlacementRowDoesNotExist() {

        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.NO_SAGA);
    }

    @Test
    void shouldClaimPendingSagaForCancellation() {

        final OutboxEventEntity event = event(OutboxEventStatus.PENDING);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.ACQUIRED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        verify(repository).save(event);
    }

    @Test
    void shouldRejectCancellationWhilePlacementLeaseIsActive() {

        final OutboxEventEntity event = event(OutboxEventStatus.PROCESSING);
        event.setClaimId("worker-a");
        event.setClaimUntil(Instant.ofEpochMilli(Long.MAX_VALUE));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.TOO_LATE);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldStealExpiredPlacementLeaseForCancellation() {

        final OutboxEventEntity event = event(OutboxEventStatus.PROCESSING);
        event.setClaimId("dead-worker");
        event.setClaimUntil(CLOCK.instant().minusMillis(1));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.ACQUIRED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(event.getClaimId()).isNull();
        assertThat(event.getClaimUntil()).isNull();
        verify(repository).save(event);
    }

    @Test
    void shouldTreatProcessingWithoutLeaseAsExpiredForCancellation() {

        final OutboxEventEntity event = event(OutboxEventStatus.PROCESSING);
        event.setClaimId("legacy-or-corrupt-worker");
        event.setClaimUntil(null);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.ACQUIRED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        verify(repository).save(event);
    }

    @Test
    void shouldResumeCancellationAlreadyInProgress() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLING);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.RESUME);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldSuppressCustomerResumeWhileCancellationRecoveryLeaseIsActive() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLING);
        event.setCancellationClaimId("recovery-owner");
        event.setCancellationClaimUntil(CLOCK.instant().plusSeconds(30));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.ALREADY_TERMINAL);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldAllowCustomerResumeAfterCancellationRecoveryLeaseExpires() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLING);
        event.setCancellationClaimId("dead-recovery-owner");
        event.setCancellationClaimUntil(CLOCK.instant().minusMillis(1));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.RESUME);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldRejectCancellationWhenSagaAlreadySent() {

        final OutboxEventEntity event = event(OutboxEventStatus.SENT);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.TOO_LATE);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldTreatCompensatedSagaAsTerminal() {

        final OutboxEventEntity event = event(OutboxEventStatus.COMPENSATED);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).isEqualTo(CancellationClaim.ALREADY_TERMINAL);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldCompleteCancellation() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLING);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        adapter.completeCancellation(ORDER_NUMBER);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
        verify(repository).save(event);
    }

    @Test
    void shouldFenceRecoveryCompletionByCancellationClaimId() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLING);
        event.setCancellationClaimId("claim-live");
        event.setCancellationClaimUntil(CLOCK.instant().plusSeconds(30));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        adapter.completeCancellation(ORDER_NUMBER, "claim-stale");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(event.getCancellationClaimId()).isEqualTo("claim-live");
        verify(repository, never()).save(event);

        adapter.completeCancellation(ORDER_NUMBER, "claim-live");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
        assertThat(event.getCancellationClaimId()).isNull();
        assertThat(event.getCancellationClaimUntil()).isNull();
        verify(repository).save(event);
    }

    @Test
    void shouldNoOpWhenCancellationAlreadyCompleted() {

        final OutboxEventEntity event = event(OutboxEventStatus.CANCELLED);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        adapter.completeCancellation(ORDER_NUMBER);

        verify(repository, never()).save(event);
    }

    @Test
    void shouldRejectCompletingUnexpectedSagaState() {

        final OutboxEventEntity event = event(OutboxEventStatus.PENDING);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> adapter.completeCancellation(ORDER_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void shouldRejectCompletingMissingSaga() {

        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.completeCancellation(ORDER_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_NUMBER);
    }

    private static OutboxEventEntity event(final OutboxEventStatus status) {

        return OutboxEventEntity.builder().id(1L).orderNumber(ORDER_NUMBER).status(status).build();
    }
}
