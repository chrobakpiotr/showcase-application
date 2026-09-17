package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for finding an order's recent siblings placed by the same customer, used as the candidate set for
 * {@link DetectDuplicateOrderOutPort}'s best-effort duplicate check. A persistence concern (querying by customer email and
 * recency), deliberately kept separate from the AI-assisted comparison itself: this port answers "what else did this customer
 * just order", the AI port answers "is any of that a likely duplicate of this one".
 */
public interface FindRecentOrdersByCustomerOutPort {

    /**
     * Finds other orders recently placed by {@code order}'s customer, most recent first, excluding {@code order} itself.
     * "Recently" and how many candidates are returned are both implementation-defined (e.g. a configurable lookback window and
     * a small maximum count) - the domain only needs "a handful of plausible candidates", not an exact history.
     *
     * @param order the just-placed {@link Order} to find candidates for.
     * @return recent orders by the same customer, most recent first; empty if none exist within the implementation's window.
     */
    List<Order> findRecentOrders(Order order);

}
