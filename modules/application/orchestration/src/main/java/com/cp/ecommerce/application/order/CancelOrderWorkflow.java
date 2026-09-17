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

}
