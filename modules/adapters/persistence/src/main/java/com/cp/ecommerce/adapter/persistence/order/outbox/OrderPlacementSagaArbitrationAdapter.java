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

        return beginCancellation(orderNumber, null);
    }

    @Override
    public CancellationClaim beginCancellation(final String orderNumber, final String claimId) {

        return outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .map(event -> claim(event, claimId))
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

    private CancellationClaim claim(final OutboxEventEntity event, final String claimId) {

        if (event.getStatus() == OutboxEventStatus.PENDING) {

            return claimId == null ? claimForCancellation(event) : CancellationClaim.LOST_CLAIM;
        }
        if (event.getStatus() == OutboxEventStatus.PROCESSING) {

            if (claimId != null) {
                return CancellationClaim.LOST_CLAIM;
            }
            return leaseExpired(event, now()) ? claimForCancellation(event) : CancellationClaim.TOO_LATE;
        }
        if (event.getStatus() == OutboxEventStatus.CANCELLING) {

            return claimCancelling(event, claimId);
        }
        if (event.getStatus() == OutboxEventStatus.SENT) {

            return CancellationClaim.TOO_LATE;
        }
        return CancellationClaim.ALREADY_TERMINAL;
    }

    private CancellationClaim claimCancelling(final OutboxEventEntity event, final String claimId) {

        if (activeCancellationRecoveryLease(event, now())) {

            if (claimId == null) {
                return CancellationClaim.BUSY;
            }
            return Objects.equals(event.getCancellationClaimId(), claimId)
                    ? CancellationClaim.RESUME
                    : CancellationClaim.LOST_CLAIM;
        }
        if (claimId != null) {
            return CancellationClaim.LOST_CLAIM;
        }
        clearExpiredCancellationOwnership(event);
        return CancellationClaim.RESUME;
    }

    private void clearExpiredCancellationOwnership(final OutboxEventEntity event) {

        if (event.getCancellationClaimId() == null && event.getCancellationClaimUntil() == null) {
            return;
        }
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        outboxEventEntityRepository.save(event);
    }

    private Instant now() {

        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }

    private CancellationClaim claimForCancellation(final OutboxEventEntity event) {

        event.setStatus(OutboxEventStatus.CANCELLING);
        event.setClaimId(null);
        event.setClaimUntil(null);
        outboxEventEntityRepository.save(event);
        return CancellationClaim.ACQUIRED;
    }

    private static boolean activeCancellationRecoveryLease(final OutboxEventEntity event, final Instant now) {

        return event.getCancellationClaimId() != null && event.getCancellationClaimUntil() != null
                && event.getCancellationClaimUntil().isAfter(now);
    }

    private static boolean leaseExpired(final OutboxEventEntity event, final Instant now) {

        return event.getClaimUntil() == null || !event.getClaimUntil().isAfter(now);
    }
}
