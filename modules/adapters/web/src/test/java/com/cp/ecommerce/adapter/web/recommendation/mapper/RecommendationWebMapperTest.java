package com.cp.ecommerce.adapter.web.recommendation.mapper;

import java.util.List;

import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RecommendationWebMapper}.
 */
class RecommendationWebMapperTest {

    private final transient RecommendationWebMapper recommendationWebMapper = new RecommendationWebMapper();

    @Test
    void shouldMapToResource() {

        final ProductRecommendations domain = ProductRecommendations.builder()
                .recommendations(
                        List.of(
                                ProductRecommendation.builder()
                                        .sku("SKU-1")
                                        .productName("Mouse")
                                        .reason("Popular with buyers of keyboards.")
                                        .build()))
                .assistantAvailable(true)
                .build();

        final var result = recommendationWebMapper.mapToResource(domain);

        assertThat(result).isPresent();
        assertThat(result.get().assistantAvailable()).isTrue();
        assertThat(result.get().recommendations()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("SKU-1");
            assertThat(item.productName()).isEqualTo("Mouse");
            assertThat(item.reason()).isEqualTo("Popular with buyers of keyboards.");
        });
    }

    @Test
    void shouldReturnEmptyWhenDomainObjectIsNull() {

        assertThat(recommendationWebMapper.mapToResource(null)).isEmpty();
    }

}
