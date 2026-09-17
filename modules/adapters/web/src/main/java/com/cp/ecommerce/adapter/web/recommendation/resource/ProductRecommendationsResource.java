package com.cp.ecommerce.adapter.web.recommendation.resource;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response body returned by the personalized recommendations endpoint.
 */
@Builder
public record ProductRecommendationsResource(List<ProductRecommendationResource> recommendations,
        @Schema(example = "true") boolean assistantAvailable) {

}
