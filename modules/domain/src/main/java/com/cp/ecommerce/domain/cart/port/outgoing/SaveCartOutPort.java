package com.cp.ecommerce.domain.cart.port.outgoing;

import com.cp.ecommerce.adapter.common.exception.CartConflictException;
import com.cp.ecommerce.domain.cart.Cart;

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
