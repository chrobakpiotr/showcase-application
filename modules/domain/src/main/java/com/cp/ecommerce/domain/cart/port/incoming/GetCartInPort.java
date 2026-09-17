package com.cp.ecommerce.domain.cart.port.incoming;

import com.cp.ecommerce.domain.cart.Cart;

/**
 * Incoming port for looking up an existing cart.
 */
public interface GetCartInPort {

    /**
     * Returns the cart for {@code cartId}, or {@code null} if no such cart exists - unlike
     * {@code inventory.GetStockLevelInPort}, this deliberately does not default to an empty value: an unknown cart id is almost
     * always a client bug (stale/expired local storage) or an already-abandoned cart, not a meaningful "zero" state worth
     * modelling (see ADR 0027).
     */
    Cart getCart(String cartId);

}
