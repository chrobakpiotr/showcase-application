package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for a best-effort AI-assisted check of whether an order is a likely accidental duplicate (e.g. a double-click
 * or resubmitted form) of one of the same customer's other recent orders. Implementations may call a locally-hosted or hosted
 * embedding model to compare the orders' free-text remarks semantically, or - when the feature is disabled/unavailable - a
 * no-op adapter that always returns {@link DuplicateOrderCheckResult#none()} without making any external call. This is a
 * best-effort secondary side-channel, complementary to (not a replacement for) the exact-fingerprint Idempotency-Key mechanism
 * in {@code PlaceOrderUseCase}: it catches near-identical, not just byte-identical, resubmissions. Like
 * {@link ClassifyOrderRemarksOutPort}, its result is never used to automatically block, cancel, or otherwise act on an order -
 * only to surface a signal for a human reviewer.
 */
public interface DetectDuplicateOrderOutPort {

    /**
     * Checks whether {@code order} is a likely duplicate of any of {@code recentOrders} (already restricted to the same
     * customer, see {@link FindRecentOrdersByCustomerOutPort}).
     *
     * @param order the just-placed {@link Order} to check.
     * @param recentOrders the same customer's other recent orders to compare against; never empty (callers should short-circuit
     *            on an empty candidate list before calling this port).
     * @return the {@link DuplicateOrderCheckResult}.
     */
    DuplicateOrderCheckResult check(Order order, List<Order> recentOrders);

}
