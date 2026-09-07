package com.cp.ecommerce.domain.recommendation;

import lombok.Builder;
import lombok.Value;

/**
 * One AI-selected catalog recommendation for a customer.
 */
@Value
@Builder
public class ProductRecommendation {

    String sku;

    String productName;

    String reason;

}
