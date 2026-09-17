package com.cp.ecommerce.domain.wishlist.port.outgoing;

import com.cp.ecommerce.domain.wishlist.Wishlist;

/**
 * Outgoing port for looking up a persisted wishlist by id.
 */
public interface FindWishlistOutPort {

    Wishlist find(String wishlistId);

}
