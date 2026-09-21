package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort.CancellationClaim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancellationActiveRecoveryClaimRedTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final Instant NOW = Instant.parse("2026-09-21T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventEntityRepository repository;

    @Test
    void customerResumeShouldNotRunSideEffectsWhileRecoveryLeaseIsActive() {

        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .status(OutboxEventStatus.CANCELLING)
                .cancellationClaimId("recovery-owner")
                .cancellationClaimUntil(NOW.plusSeconds(30))
                .build();
        given(repository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(event));

        final OrderPlacementSagaArbitrationAdapter adapter = new OrderPlacementSagaArbitrationAdapter(repository, CLOCK);

        assertThat(adapter.beginCancellation(ORDER_NUMBER)).as(
                "active recovery ownership must prevent customer path from concurrently replaying cancellation side effects")
                .isEqualTo(CancellationClaim.ALREADY_TERMINAL);

        verify(repository, never()).save(event);
    }
}
