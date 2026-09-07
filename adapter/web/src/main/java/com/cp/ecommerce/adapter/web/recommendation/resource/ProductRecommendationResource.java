package com.cp.ecommerce.adapter.web.recommendation.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * One personalized product recommendation.
 */
@Builder
public record ProductRecommendationResource(@Schema(example = "SKU-2001") String sku,
        @Schema(example = "Wireless Keyboard") String productName,
        @Schema(example = "Pairs naturally with the mouse you bought recently.") String reason) {

}
