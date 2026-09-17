package com.cp.ecommerce.adapter.web.review.resource;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a SKU's aggregate rating.
 */
@Builder
public record ReviewSummaryResource(@Schema(example = "SKU-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String sku,
        @Schema(example = "4.5") BigDecimal averageRating, @Schema(example = "12") long reviewCount) {

}
