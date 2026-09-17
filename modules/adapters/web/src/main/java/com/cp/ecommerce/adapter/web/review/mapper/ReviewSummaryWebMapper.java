package com.cp.ecommerce.adapter.web.review.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewSummaryResource;
import com.cp.ecommerce.domain.review.ReviewSummary;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link ReviewSummary} domain object to its web resource.
 */
@Component
public class ReviewSummaryWebMapper implements WebResponseMapper<ReviewSummary, ReviewSummaryResource> {

    @Override
    public Optional<ReviewSummaryResource> mapToResource(final ReviewSummary reviewSummary) {

        return Optional.ofNullable(reviewSummary)
                .map(
                        domain -> ReviewSummaryResource.builder()
                                .sku(domain.sku())
                                .averageRating(domain.averageRating())
                                .reviewCount(domain.reviewCount())
                                .build());
    }

}
