package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for triggering a best-effort AI-assisted check of whether an order is a likely accidental duplicate of one of
 * the same customer's other recent orders.
 */
public interface DetectDuplicateOrderInPort {

    /**
     * Checks the given order for likely duplicates among the same customer's recent orders.
     *
     * @param order {@link Order} to check.
     * @return the {@link DuplicateOrderCheckResult}.
     */
    DuplicateOrderCheckResult detectDuplicate(Order order);

}
