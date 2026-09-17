package com.cp.ecommerce.adapter.persistence.order.outbox;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort;

/**
 * PostgreSQL-backed arbiter shared by customer cancellation and placement-saga polling.
 */
@PersistenceAdapter
class OrderPlacementSagaArbitrationAdapter implements OrderPlacementSagaArbitrationOutPort {

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    OrderPlacementSagaArbitrationAdapter(final OutboxEventEntityRepository outboxEventEntityRepository) {

        this.outboxEventEntityRepository = outboxEventEntityRepository;
    }

    @Override
    public CancellationClaim beginCancellation(final String orderNumber) {

        return outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                .map(this::claim)
                .orElse(CancellationClaim.NO_SAGA);
    }

    @Override
    public void completeCancellation(final String orderNumber) {

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
        event.setStatus(OutboxEventStatus.CANCELLED);
        outboxEventEntityRepository.save(event);
    }

    private CancellationClaim claim(final OutboxEventEntity event) {

        return switch (event.getStatus()) {
        case PENDING -> {
            event.setStatus(OutboxEventStatus.CANCELLING);
            outboxEventEntityRepository.save(event);
            yield CancellationClaim.ACQUIRED;
        }
        case CANCELLING -> CancellationClaim.RESUME;
        case CANCELLED, COMPENSATED -> CancellationClaim.ALREADY_TERMINAL;
        case SENT -> CancellationClaim.TOO_LATE;
        };
    }
}
