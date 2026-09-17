package com.cp.ecommerce.adapter.ai.recommendation;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.outgoing.GenerateProductRecommendationsOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Default no-op adapter used when AI recommendations are disabled.
 */
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotGenerateProductRecommendationsAdapter implements GenerateProductRecommendationsOutPort {

    @Override
    public ProductRecommendations recommend(
            final RecommendationRequest request,
            final List<PurchasedProductProfile> purchasedProducts,
            final List<CustomerReviewProfile> reviews,
            final List<RecommendationCandidateProduct> candidates) {

        return ProductRecommendations.unavailable();
    }

}
