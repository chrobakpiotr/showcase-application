package com.cp.ecommerce.domain.recommendation;

import lombok.Builder;
import lombok.Value;

/**
 * Catalog product eligible to be recommended.
 */
@Value
@Builder
public class RecommendationCandidateProduct {

    String sku;

    String productName;

    String description;

    String categoryName;

}
