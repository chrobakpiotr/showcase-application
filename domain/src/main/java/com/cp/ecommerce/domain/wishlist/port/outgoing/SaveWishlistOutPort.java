package com.cp.ecommerce.domain.wishlist.port.outgoing;

import com.cp.ecommerce.adapter.common.exception.WishlistConflictException;
import com.cp.ecommerce.domain.wishlist.Wishlist;

/**
 * Outgoing port for persisting a wishlist.
 */
public interface SaveWishlistOutPort {

    /**
     * @throws WishlistConflictException if the persisted row's version no longer matches {@code wishlist.getVersion()}.
     */
    Wishlist save(Wishlist wishlist);

}
