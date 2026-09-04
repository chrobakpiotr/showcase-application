package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a cart update loses an optimistic-locking race - two concurrent requests tried to mutate the same cart
 * at once. Mapped to {@code 409 Conflict}.
 *
 * <p>
 * Unlike {@code StockLevelConflictException}, this is deliberately <b>not</b> retried server-side: a shopping cart is
 * single-actor by design (one customer's own browser tabs/devices), so a bounded retry loop coordinating concurrent writers -
 * the point of {@code ManageStockUseCase}'s retry logic - has no real counterpart here. Surfacing the conflict once and letting
 * the client re-fetch and retry is sufficient (see ADR 0027).
 */
public class CartConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CartConflictException(final String cartId, final Throwable cause) {

        super("Concurrent cart modification detected for cart " + cartId + ", please retry", cause);
    }

}
