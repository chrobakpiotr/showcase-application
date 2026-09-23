package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageOrderCancellationRecoveryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String ORDER_NUMBER = "ORDER-1";

    @Mock
    private OutboxEventEntityRepository repository;
    private ManageOrderCancellationRecoveryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManageOrderCancellationRecoveryAdapter(repository, 30_000L, 5_000L, 2);
    }

    @Test
    void shouldFindAndClaimDueCancellation() {
        given(repository.findDueCancellationOrderNumbers(eq(OutboxEventStatus.CANCELLING), eq(NOW), any(Pageable.class)))
                .willReturn(List.of(ORDER_NUMBER));
        final OutboxEventEntity event = event(0);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.findDueCancellationOrderNumbers(NOW, 10)).containsExactly(ORDER_NUMBER);
        final OrderCancellationRecoveryClaim claim = adapter.claim(ORDER_NUMBER, NOW);
        assertThat(claim.claimId()).isNotBlank();
        assertThat(event.getCancellationAttempts())
                .as("claim acquisition alone must not consume a failure attempt while cancellation may only be waiting")
                .isZero();
        assertThat(event.getCancellationClaimUntil()).isEqualTo(NOW.plusMillis(30_000L));
    }

    @Test
    void shouldNotStealActiveCancellationLease() {
        final OutboxEventEntity event = event(0);
        event.setCancellationClaimUntil(NOW.plusSeconds(1));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        assertThat(adapter.claim(ORDER_NUMBER, NOW)).isNull();
        verify(repository, never()).save(event);
    }

    @Test
    void shouldParkExhaustedCancellation() {
        final OutboxEventEntity event = event(2);
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        assertThat(adapter.claim(ORDER_NUMBER, NOW)).isNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.MANUAL_REVIEW);
    }

    @Test
    void shouldScheduleOwnedFailureAndFenceStaleFailure() {
        final OutboxEventEntity event = event(0);
        event.setCancellationClaimId("claim-1");
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        adapter.recordFailure(ORDER_NUMBER, "stale", "ignored", NOW);
        verify(repository, never()).save(event);
        adapter.recordFailure(ORDER_NUMBER, "claim-1", "temporary", NOW);
        assertThat(event.getCancellationNextAttemptDate()).isEqualTo(NOW.plusMillis(5_000L));
        assertThat(event.getCancellationClaimId()).isNull();
        assertThat(event.getCancellationLastError()).isEqualTo("temporary");
        assertThat(event.getCancellationAttempts()).isEqualTo(1);
        verify(repository).save(event);
    }

    @Test
    void shouldReleaseOwnedWaitingClaimWithoutConsumingFailureAttempt() {

        final OutboxEventEntity event = event(1);
        event.setCancellationClaimId("claim-waiting");
        event.setCancellationClaimUntil(NOW.plusSeconds(30));
        event.setCancellationLastError("old failure");
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        adapter.recordWaiting(ORDER_NUMBER, "claim-waiting", NOW);

        assertThat(event.getCancellationAttempts()).isEqualTo(1);
        assertThat(event.getCancellationClaimId()).isNull();
        assertThat(event.getCancellationClaimUntil()).isNull();
        assertThat(event.getCancellationLastError()).isNull();
        assertThat(event.getCancellationNextAttemptDate()).isEqualTo(NOW.plusMillis(5_000L));
        verify(repository).save(event);
    }

    @Test
    void shouldFenceSuccessAndIgnoreAlreadyCancelledRow() {
        final OutboxEventEntity event = event(1);
        event.setCancellationClaimId("claim-2");
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        adapter.recordSuccess(ORDER_NUMBER, "stale-success");
        assertThat(event.getCancellationClaimId()).isEqualTo("claim-2");
        verify(repository, never()).save(event);

        adapter.recordSuccess(ORDER_NUMBER, "claim-2");
        assertThat(event.getCancellationClaimId()).isNull();
        event.setStatus(OutboxEventStatus.CANCELLED);
        adapter.recordSuccess(ORDER_NUMBER, "anything");
    }

    @Test
    void shouldParkOwnedFailureAtMaximumAttempts() {
        final OutboxEventEntity event = event(1);
        event.setCancellationClaimId("claim-3");
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));
        adapter.recordFailure(ORDER_NUMBER, "claim-3", "still failing", NOW);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.MANUAL_REVIEW);
        assertThat(event.getCancellationAttempts()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreWaitingBookkeepingForMissingAndStaleClaims() {

        given(repository.findByOrderNumberForUpdate("missing-waiting")).willReturn(Optional.empty());

        adapter.recordWaiting("missing-waiting", "claim-missing", NOW.plusSeconds(5));

        final OutboxEventEntity stale = event(0);
        stale.setCancellationClaimId("claim-live");
        given(repository.findByOrderNumberForUpdate("stale-waiting")).willReturn(Optional.of(stale));

        adapter.recordWaiting("stale-waiting", "claim-stale", NOW.plusSeconds(5));

        verify(repository, never()).save(stale);
    }

    private static OutboxEventEntity event(final int attempts) {
        return OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .status(OutboxEventStatus.CANCELLING)
                .createdDate(NOW.minusSeconds(10))
                .nextAttemptDate(NOW)
                .cancellationAttempts(attempts)
                .cancellationNextAttemptDate(NOW)
                .build();
    }
}
