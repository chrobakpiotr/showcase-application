package com.cp.ecommerce.domain.recommendation;

import lombok.Builder;
import lombok.Value;

/**
 * Lightweight purchase-history signal exposed to the recommendation use case.
 */
@Value
@Builder
public class PurchasedProductProfile {

    String sku;

    String productName;

    int totalQuantity;

}
