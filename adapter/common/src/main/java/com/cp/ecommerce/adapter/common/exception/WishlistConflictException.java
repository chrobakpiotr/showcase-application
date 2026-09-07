package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a wishlist update loses an optimistic-locking race.
 */
public class WishlistConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WishlistConflictException(final String wishlistId, final Throwable cause) {

        super("Concurrent wishlist modification detected for wishlist " + wishlistId + ", please retry", cause);
    }

}
