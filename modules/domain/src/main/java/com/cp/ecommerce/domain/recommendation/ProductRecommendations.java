package com.cp.ecommerce.domain.recommendation;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Structured response returned by the personalized recommendations feature.
 */
@Value
@Builder
public class ProductRecommendations {

    @Builder.Default
    List<ProductRecommendation> recommendations = List.of();

    boolean assistantAvailable;

    public static ProductRecommendations unavailable() {

        return ProductRecommendations.builder().recommendations(List.of()).assistantAvailable(false).build();
    }

}
