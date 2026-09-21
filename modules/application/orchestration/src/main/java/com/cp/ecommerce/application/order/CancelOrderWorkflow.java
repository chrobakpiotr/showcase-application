package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.order.Order;

/**
 * Application-level orchestration boundary for customer-initiated order cancellation.
 */
public interface CancelOrderWorkflow {

    /**
     * Executes the existing cancellation workflow without changing its domain semantics.
     *
     * @param orderNumber order business identifier.
     * @return cancelled order, or {@code null} when the order does not exist.
     */
    Order cancelOrder(String orderNumber);

    /**
     * Resume cancellation under an already acquired durable recovery claim.
     *
     * @param orderNumber order business identifier.
     * @param claimId durable recovery fencing token.
     * @return cancelled order, or {@code null} when the order does not exist.
     */
    Order cancelOrder(String orderNumber, String claimId);

    /**
     * Resume an autonomous cancellation recovery pass under an existing durable claim.
     *
     * @param orderNumber order business identifier.
     * @param claimId durable recovery fencing token.
     * @return durable recovery outcome for scheduler bookkeeping.
     */
    CancellationRecoveryOutcome recoverCancellation(String orderNumber, String claimId);

}
