package com.cp.ecommerce.adapter.ai.recommendation;

import java.util.List;

import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DoNotGenerateProductRecommendationsAdapter}.
 */
class DoNotGenerateProductRecommendationsAdapterTest {

    @Test
    void shouldReturnUnavailableFallback() {

        final ProductRecommendations result = new DoNotGenerateProductRecommendationsAdapter().recommend(
                RecommendationRequest.builder().customerEmail("john.doe@test.com").build(),
                List.of(),
                List.of(),
                List.of(RecommendationCandidateProduct.builder().sku("SKU-1").productName("Mouse").build()));

        assertThat(result).isEqualTo(ProductRecommendations.unavailable());
    }

}
