package com.cp.ecommerce.adapter.web.wishlist.resource;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a remembered wishlist item.
 */
@Builder
public record WishlistItemResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "Wireless Mouse") String productName, @Schema(example = "2026-09-07T12:00:00.000Z") Date addedDate) {

}
