package com.cp.ecommerce.adapter.web.wishlist.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for {@code POST /api/wishlist/{wishlistId}/items}.
 */
public record AddWishlistItemResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku) {

}
