package com.cp.ecommerce.domain.cart.port.outgoing;

import com.cp.ecommerce.domain.cart.Cart;

/**
 * Outgoing port for looking up a persisted cart by id.
 */
public interface FindCartOutPort {

    /**
     * Returns the cart for {@code cartId}, or {@code null} if none exists.
     */
    Cart find(String cartId);

}
