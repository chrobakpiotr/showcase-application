package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort;

/**
 * PostgreSQL-backed arbiter shared by customer cancellation and placement-saga polling.
 */
@PersistenceAdapter
class OrderPlacementSagaArbitrationAdapter implements OrderPlacementSagaArbitrationOutPort {

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    private final Clock clock;

    OrderPlacementSagaArbitrationAdapter(final OutboxEventEntityRepository outboxEventEntityRepository, final Clock clock) {
        this.outboxEventEntityRepository = outboxEventEntityRepository;
        this.clock = clock;
    }

    @Override
    public CancellationClaim beginCancellation(final String orderNumber) {

        return outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .map(this::claim)
                .orElse(CancellationClaim.NO_SAGA);
    }

    @Override
    public void completeCancellation(final String orderNumber) {

        completeCancellation(orderNumber, null);
    }

    @Override
    public void completeCancellation(final String orderNumber, final String claimId) {

        final OutboxEventEntity event = outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Placement saga event disappeared while completing cancellation for order: " + orderNumber));
        if (event.getStatus() == OutboxEventStatus.CANCELLED) {

            return;
        }
        if (event.getStatus() != OutboxEventStatus.CANCELLING) {

            throw new IllegalStateException(
                    "Cannot complete customer cancellation from saga state " + event.getStatus() + " for order: "
                            + orderNumber);
        }
        if (!Objects.equals(event.getCancellationClaimId(), claimId)) {

            return;
        }
        event.setStatus(OutboxEventStatus.CANCELLED);
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        event.setCancellationLastError(null);
        outboxEventEntityRepository.save(event);
    }

    private CancellationClaim claim(final OutboxEventEntity event) {

        if (event.getStatus() == OutboxEventStatus.PENDING) {

            return claimForCancellation(event);
        }
        if (event.getStatus() == OutboxEventStatus.PROCESSING) {

            return leaseExpired(event, Instant.ofEpochMilli(clock.instant().toEpochMilli()))
                    ? claimForCancellation(event)
                    : CancellationClaim.TOO_LATE;
        }
        if (event.getStatus() == OutboxEventStatus.CANCELLING) {

            return CancellationClaim.RESUME;
        }
        if (event.getStatus() == OutboxEventStatus.SENT) {

            return CancellationClaim.TOO_LATE;
        }
        return CancellationClaim.ALREADY_TERMINAL;
    }

    private CancellationClaim claimForCancellation(final OutboxEventEntity event) {

        event.setStatus(OutboxEventStatus.CANCELLING);
        event.setClaimId(null);
        event.setClaimUntil(null);
        outboxEventEntityRepository.save(event);
        return CancellationClaim.ACQUIRED;
    }

    private static boolean leaseExpired(final OutboxEventEntity event, final Instant now) {

        return event.getClaimUntil() == null || !event.getClaimUntil().isAfter(now);
    }
}
