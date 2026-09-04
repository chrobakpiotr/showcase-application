package com.cp.ecommerce.adapter.web.cart.resource;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a shopping cart, returned by every cart endpoint.
 */
@Builder
public record CartResource(@Schema(example = "CART-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String cartId,
        List<CartLineItemResource> items, @Schema(example = "59.98") BigDecimal total, @Schema(example = "2") int itemCount) {

}
