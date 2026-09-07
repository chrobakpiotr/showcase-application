package com.cp.ecommerce.adapter.web.recommendation.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.recommendation.resource.ProductRecommendationResource;
import com.cp.ecommerce.adapter.web.recommendation.resource.ProductRecommendationsResource;
import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for translating recommendation domain responses into web resources.
 */
@Component
public class RecommendationWebMapper implements WebResponseMapper<ProductRecommendations, ProductRecommendationsResource> {

    @Override
    public Optional<ProductRecommendationsResource> mapToResource(final ProductRecommendations recommendations) {

        return Optional.ofNullable(recommendations)
                .map(
                        domain -> ProductRecommendationsResource.builder()
                                .recommendations(domain.getRecommendations().stream().map(this::mapSingle).toList())
                                .assistantAvailable(domain.isAssistantAvailable())
                                .build());
    }

    private ProductRecommendationResource mapSingle(final ProductRecommendation recommendation) {

        return ProductRecommendationResource.builder()
                .sku(recommendation.getSku())
                .productName(recommendation.getProductName())
                .reason(recommendation.getReason())
                .build();
    }

}
