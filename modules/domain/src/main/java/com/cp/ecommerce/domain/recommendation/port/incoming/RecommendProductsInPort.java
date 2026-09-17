package com.cp.ecommerce.domain.recommendation.port.incoming;

import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;

/**
 * Incoming port for AI-powered personalized product recommendations.
 */
public interface RecommendProductsInPort {

    ProductRecommendations recommendProducts(RecommendationRequest request);

}
