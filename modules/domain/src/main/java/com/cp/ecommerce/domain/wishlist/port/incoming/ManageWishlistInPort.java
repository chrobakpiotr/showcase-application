package com.cp.ecommerce.domain.wishlist.port.incoming;

import com.cp.ecommerce.domain.wishlist.Wishlist;

/**
 * Incoming port for every mutation of a wishlist's remembered items.
 */
public interface ManageWishlistInPort {

    Wishlist addItem(String wishlistId, String sku, String productName);

    Wishlist removeItem(String wishlistId, String sku);

}
