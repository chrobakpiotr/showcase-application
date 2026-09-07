package com.cp.ecommerce.domain.wishlist.port.outgoing;

/**
 * Outgoing port for generating a new, unique wishlist id on wishlist creation.
 */
public interface GenerateWishlistIdOutPort {

    String generate();

}
