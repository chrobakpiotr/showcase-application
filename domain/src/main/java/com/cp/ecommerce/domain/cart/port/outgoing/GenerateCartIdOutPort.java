package com.cp.ecommerce.domain.cart.port.outgoing;

/**
 * Outgoing port for generating a new, unique cart id on cart creation.
 */
public interface GenerateCartIdOutPort {

    String generate();

}
