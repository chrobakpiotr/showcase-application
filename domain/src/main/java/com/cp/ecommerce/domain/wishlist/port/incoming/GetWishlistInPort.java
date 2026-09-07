package com.cp.ecommerce.domain.wishlist.port.incoming;

import com.cp.ecommerce.domain.wishlist.Wishlist;

/**
 * Incoming port for looking up an existing wishlist.
 */
public interface GetWishlistInPort {

    /**
     * Returns the wishlist for {@code wishlistId}, or {@code null} if no such wishlist exists.
     */
    Wishlist getWishlist(String wishlistId);

}
