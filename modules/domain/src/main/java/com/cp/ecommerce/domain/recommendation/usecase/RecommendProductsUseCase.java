package com.cp.ecommerce.domain.recommendation.usecase;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.incoming.RecommendProductsInPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerPurchaseHistoryOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerReviewHistoryOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindRecommendationCandidateProductsOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.GenerateProductRecommendationsOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for AI-powered personalized catalog recommendations.
 */
@UseCase
@RequiredArgsConstructor
public class RecommendProductsUseCase implements RecommendProductsInPort {

    private final FindCustomerPurchaseHistoryOutPort findCustomerPurchaseHistoryOutPort;

    private final FindCustomerReviewHistoryOutPort findCustomerReviewHistoryOutPort;

    private final FindRecommendationCandidateProductsOutPort findRecommendationCandidateProductsOutPort;

    private final GenerateProductRecommendationsOutPort generateProductRecommendationsOutPort;

    @Override
    public ProductRecommendations recommendProducts(final RecommendationRequest request) {

        final List<PurchasedProductProfile> purchasedProducts = findCustomerPurchaseHistoryOutPort
                .findPurchasedProducts(request.getCustomerEmail());
        final List<CustomerReviewProfile> reviews = findCustomerReviewHistoryOutPort.findReviews(request.getCustomerEmail());
        final Set<String> purchasedSkus = purchasedProducts.stream()
                .map(PurchasedProductProfile::getSku)
                .collect(Collectors.toSet());
        final List<RecommendationCandidateProduct> candidates = findRecommendationCandidateProductsOutPort
                .findCandidates(purchasedSkus);
        if (candidates.isEmpty()) {
            return ProductRecommendations.builder().recommendations(List.of()).assistantAvailable(true).build();
        }
        return generateProductRecommendationsOutPort.recommend(request, purchasedProducts, reviews, candidates);
    }

}
