package com.cp.ecommerce.adapter.persistence.wishlist;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.wishlist.port.outgoing.GenerateWishlistIdOutPort;

/**
 * Implementation of {@link GenerateWishlistIdOutPort}.
 */
@PersistenceAdapter
class GenerateWishlistIdAdapter implements GenerateWishlistIdOutPort {

    private static final String WISHLIST_ID_PREFIX = "WISHLIST-";

    @Override
    public String generate() {

        return WISHLIST_ID_PREFIX + UUID.randomUUID();
    }

}
