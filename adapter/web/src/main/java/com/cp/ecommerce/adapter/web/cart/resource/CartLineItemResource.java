package com.cp.ecommerce.adapter.web.cart.resource;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a single line item within a {@link CartResource}.
 */
@Builder
public record CartLineItemResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "Wireless Mouse") String productName, @Schema(example = "29.99") BigDecimal unitPrice,
        @Schema(example = "2") int quantity, @Schema(example = "59.98") BigDecimal subtotal) {

}
