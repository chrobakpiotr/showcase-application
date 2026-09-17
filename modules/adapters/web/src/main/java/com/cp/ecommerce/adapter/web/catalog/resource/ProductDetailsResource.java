package com.cp.ecommerce.adapter.web.catalog.resource;

import java.math.BigDecimal;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the full product details returned by {@code GET /api/catalog/products/{sku}} and the products listing
 * endpoint.
 */
@Builder
public record ProductDetailsResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "Wireless Headphones") String name,
        @Schema(example = "Over-ear, noise-cancelling.") String description,
        @Schema(example = "electronics") String categorySlug, @Schema(example = "Electronics") String categoryName,
        @Schema(example = "99.99") BigDecimal unitPrice,
        @Schema(example = "https://example.com/headphones.png") String imageUrl, @Schema(example = "true") boolean active,
        @Schema(example = "2024-03-15T10:30:00.000Z") Date created) {

}
