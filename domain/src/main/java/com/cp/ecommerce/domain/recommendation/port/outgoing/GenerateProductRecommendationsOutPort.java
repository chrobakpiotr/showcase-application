package com.cp.ecommerce.domain.recommendation.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;

/**
 * Outgoing port for generating structured product recommendations via the configured AI adapter.
 */
public interface GenerateProductRecommendationsOutPort {

    ProductRecommendations recommend(
            RecommendationRequest request,
            List<PurchasedProductProfile> purchasedProducts,
            List<CustomerReviewProfile> reviews,
            List<RecommendationCandidateProduct> candidates);

}
