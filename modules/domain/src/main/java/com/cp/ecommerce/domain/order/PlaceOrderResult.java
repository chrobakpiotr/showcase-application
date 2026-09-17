package com.cp.ecommerce.domain.order;

/**
 * Result of {@link com.cp.ecommerce.domain.order.port.incoming.PlaceOrderInPort#placeOrder(Order, String)}.
 *
 * @param orderNumber number of the order the caller should treat as the result of this request.
 * @param newlyPlaced {@code true} only if this call actually persisted a new order; {@code false} if it replayed a previously
 *            completed idempotent request - so callers such as business metrics don't double-count work that didn't actually
 *            happen.
 */
public record PlaceOrderResult(String orderNumber, boolean newlyPlaced) {

}
