package com.cp.ecommerce.domain.wishlist.port.incoming;

import com.cp.ecommerce.domain.wishlist.Wishlist;

/**
 * Incoming port for starting a new, empty wishlist.
 */
public interface CreateWishlistInPort {

    Wishlist createWishlist();

}
