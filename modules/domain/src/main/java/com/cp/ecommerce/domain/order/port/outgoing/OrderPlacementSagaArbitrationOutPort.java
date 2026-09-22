package com.cp.ecommerce.domain.order.port.outgoing;

/**
 * Persistence boundary used to serialize customer cancellation against the order-placement saga.
 *
 * <p>
 * Implementations must lock the order's durable placement-process row before returning a decision.
 */
public interface OrderPlacementSagaArbitrationOutPort {

    /**
     * Result of trying to claim the placement process for customer cancellation.
     */
    enum CancellationClaim {

        ACQUIRED,
        RESUME,
        BUSY,
        LOST_CLAIM,
        ALREADY_TERMINAL,
        TOO_LATE,
        NO_SAGA
    }

    /**
     * Claim cancellation while holding the placement saga's durable arbitration lock.
     *
     * @param orderNumber order business key.
     * @return arbitration decision.
     */
    CancellationClaim beginCancellation(String orderNumber);

    /**
     * Resume a recovery-owned cancellation while holding the same durable arbitration lock.
     *
     * @param orderNumber order business key.
     * @param claimId cancellation recovery fencing token.
     * @return arbitration decision.
     */
    CancellationClaim beginCancellation(String orderNumber, String claimId);

    /**
     * Mark a previously claimed customer cancellation as fully completed.
     *
     * @param orderNumber order business key.
     */
    void completeCancellation(String orderNumber);

    /**
     * Mark recovery-owned cancellation complete only when the durable claim still matches.
     *
     * @param orderNumber order business key.
     * @param claimId cancellation recovery fencing token.
     */
    void completeCancellation(String orderNumber, String claimId);
}
