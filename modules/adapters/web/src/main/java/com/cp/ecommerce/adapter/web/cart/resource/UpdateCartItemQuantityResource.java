package com.cp.ecommerce.adapter.web.cart.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for {@code PUT /api/cart/{cartId}/items/{sku}}.
 */
public record UpdateCartItemQuantityResource(@Schema(example = "3") Integer quantity) {

}
