package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacementSagaCancellationCompletionTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String OWNER = "owner-a";
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventEntityRepository repository;

    private OrderPlacementSagaArbitrationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrderPlacementSagaArbitrationAdapter(repository, CLOCK);
    }

    @Test
    void shouldCompleteOnlyCurrentUnexpiredRecoveryOwner() {
        final OutboxEventEntity event = cancelling();
        event.setCancellationClaimId(OWNER);
        event.setCancellationClaimUntil(NOW.plusSeconds(30));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.completeCancellation(ORDER_NUMBER, OWNER)).isEqualTo(CancellationCompletionOutcome.COMPLETED);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
        assertThat(event.getCancellationClaimId()).isNull();
        verify(repository).save(event);
    }

    @Test
    void shouldFenceForeignAndExpiredRecoveryOwners() {
        final OutboxEventEntity foreign = cancelling();
        foreign.setCancellationClaimId(OWNER);
        foreign.setCancellationClaimUntil(NOW.plusSeconds(30));
        given(repository.findByOrderNumberForUpdate("foreign")).willReturn(Optional.of(foreign));

        assertThat(adapter.completeCancellation("foreign", "owner-b")).isEqualTo(CancellationCompletionOutcome.LOST_CLAIM);
        verify(repository, never()).save(foreign);

        final OutboxEventEntity expired = cancelling();
        expired.setCancellationClaimId(OWNER);
        expired.setCancellationClaimUntil(NOW);
        given(repository.findByOrderNumberForUpdate("expired")).willReturn(Optional.of(expired));

        assertThat(adapter.completeCancellation("expired", OWNER)).isEqualTo(CancellationCompletionOutcome.LOST_CLAIM);
        verify(repository, never()).save(expired);
    }

    @Test
    void shouldFenceCustomerFinalizationWhileRecoveryOwnerExists() {
        final OutboxEventEntity event = cancelling();
        event.setCancellationClaimId(OWNER);
        event.setCancellationClaimUntil(NOW.plusSeconds(30));
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        assertThat(adapter.completeCancellation(ORDER_NUMBER)).isEqualTo(CancellationCompletionOutcome.LOST_CLAIM);
        verify(repository, never()).save(event);
    }

    @Test
    void shouldExposeTerminalReplayAndUnexpectedState() {
        final OutboxEventEntity terminal = cancelling();
        terminal.setStatus(OutboxEventStatus.CANCELLED);
        given(repository.findByOrderNumberForUpdate("terminal")).willReturn(Optional.of(terminal));

        assertThat(adapter.completeCancellation("terminal")).isEqualTo(CancellationCompletionOutcome.ALREADY_COMPLETED);

        final OutboxEventEntity pending = cancelling();
        pending.setStatus(OutboxEventStatus.PENDING);
        given(repository.findByOrderNumberForUpdate("pending")).willReturn(Optional.of(pending));

        assertThat(adapter.completeCancellation("pending")).isEqualTo(CancellationCompletionOutcome.CONFLICT);
    }

    private static OutboxEventEntity cancelling() {
        return OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .status(OutboxEventStatus.CANCELLING)
                .createdDate(NOW.minusSeconds(60))
                .nextAttemptDate(NOW.minusSeconds(60))
                .build();
    }
}
