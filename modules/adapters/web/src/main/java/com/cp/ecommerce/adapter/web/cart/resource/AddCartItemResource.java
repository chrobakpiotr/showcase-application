package com.cp.ecommerce.adapter.web.cart.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for {@code POST /api/cart/{cartId}/items}: the SKU/quantity a customer wants to add. Price and product name
 * are deliberately not accepted from the client - the controller resolves them authoritatively via
 * {@code ManageProductInPort.findProduct} before delegating to the cart use case (see ADR 0027).
 */
public record AddCartItemResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "2") Integer quantity) {

}
