package com.cp.ecommerce.domain.recommendation;

import lombok.Builder;
import lombok.Value;

/**
 * Lightweight review-history signal exposed to the recommendation use case.
 */
@Value
@Builder
public class CustomerReviewProfile {

    String sku;

    int rating;

    String comment;

}
