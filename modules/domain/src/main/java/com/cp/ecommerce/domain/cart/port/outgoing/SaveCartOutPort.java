package com.cp.ecommerce.domain.cart.port.outgoing;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.foundation.exception.CartConflictException;

/**
 * Outgoing port for persisting a cart.
 */
public interface SaveCartOutPort {

    /**
     * @throws CartConflictException if the persisted row's version no longer matches {@code cart.getVersion()} - see
     *             {@code SaveCartAdapter}.
     */
    Cart save(Cart cart);

}
