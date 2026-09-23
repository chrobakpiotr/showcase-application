package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;

/**
 * Persistence boundary used to serialize customer cancellation against the order-placement saga.
 *
 * <p>
 * Implementations must lock the order's durable placement-process row before returning a decision.
 */
public interface OrderPlacementSagaArbitrationOutPort {

    enum CancellationClaim {
        ACQUIRED,
        RESUME,
        BUSY,
        LOST_CLAIM,
        ALREADY_TERMINAL,
        TOO_LATE,
        NO_SAGA
    }

    CancellationClaim beginCancellation(String orderNumber);

    CancellationClaim beginCancellation(String orderNumber, String claimId);

    CancellationCompletionOutcome completeCancellation(String orderNumber);

    CancellationCompletionOutcome completeCancellation(String orderNumber, String claimId);
}
