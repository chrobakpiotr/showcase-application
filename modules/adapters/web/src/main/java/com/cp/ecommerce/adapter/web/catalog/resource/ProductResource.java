package com.cp.ecommerce.adapter.web.catalog.resource;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the request payload for creating or updating a catalog product.
 * <p>
 * {@code categorySlug} is a public, stable reference to an existing {@link CategoryResource} - the server resolves it to the
 * full category server-side rather than trusting a client-supplied category name (see
 * {@code com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort}'s Javadoc for the underlying rationale). Ignored on
 * update, since re-categorization is out of scope for v1.
 */
@Builder
public record ProductResource(@Schema(example = "Wireless Headphones") String name,
        @Schema(example = "Over-ear, noise-cancelling.") String description,
        @Schema(example = "electronics") String categorySlug, @Schema(example = "99.99") BigDecimal unitPrice,
        @Schema(example = "https://example.com/headphones.png") String imageUrl, @Schema(example = "true") boolean active) {

}
