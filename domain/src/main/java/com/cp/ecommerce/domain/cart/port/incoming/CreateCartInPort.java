package com.cp.ecommerce.domain.cart.port.incoming;

import com.cp.ecommerce.domain.cart.Cart;

/**
 * Incoming port for starting a new, empty shopping cart.
 */
public interface CreateCartInPort {

    Cart createCart();

}
