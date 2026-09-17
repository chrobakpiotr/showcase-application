package com.cp.ecommerce.adapter.persistence.cart;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.cart.port.outgoing.GenerateCartIdOutPort;

/**
 * Implementation of {@link GenerateCartIdOutPort}, mirroring {@code GenerateSkuAdapter}: a pure random UUID is sufficient to
 * guarantee uniqueness, no dialect-specific sequence needed.
 */
@PersistenceAdapter
class GenerateCartIdAdapter implements GenerateCartIdOutPort {

    private static final String CART_ID_PREFIX = "CART-";

    @Override
    public String generate() {

        return CART_ID_PREFIX + UUID.randomUUID();
    }

}
