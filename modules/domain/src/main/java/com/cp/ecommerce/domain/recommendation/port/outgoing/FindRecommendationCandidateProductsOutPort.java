package com.cp.ecommerce.domain.recommendation.port.outgoing;

import java.util.List;
import java.util.Set;

import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;

/**
 * Outgoing port for loading recommendable catalog products while excluding already-purchased SKUs.
 */
public interface FindRecommendationCandidateProductsOutPort {

    List<RecommendationCandidateProduct> findCandidates(Set<String> excludedSkus);

}
