package com.cp.ecommerce.adapter.ai.recommendation;

import java.util.List;

/**
 * Raw structured JSON response returned by the chat model for personalized recommendations.
 */
record ProductRecommendationsResponse(List<ProductRecommendationResponse> recommendations) {

}
