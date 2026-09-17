package com.cp.ecommerce.adapter.web.review.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.domain.review.Review;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Review} domain object to its web resource.
 */
@Component
public class ReviewWebMapper implements WebResponseMapper<Review, ReviewResource> {

    @Override
    public Optional<ReviewResource> mapToResource(final Review review) {

        return Optional.ofNullable(review)
                .map(
                        domain -> ReviewResource.builder()
                                .reviewId(domain.getReviewId())
                                .sku(domain.getSku())
                                .authorName(domain.getAuthorName())
                                .rating(domain.getRating())
                                .comment(domain.getComment())
                                .status(domain.getStatus().name())
                                .created(domain.getCreated())
                                .build());
    }

}
