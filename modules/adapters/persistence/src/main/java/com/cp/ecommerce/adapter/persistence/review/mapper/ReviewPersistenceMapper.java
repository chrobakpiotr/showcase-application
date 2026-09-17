package com.cp.ecommerce.adapter.persistence.review.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.domain.review.Review;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Review} object into/from entity object.
 */
@Component
public class ReviewPersistenceMapper implements PersistenceMapper<Review, ReviewEntity> {

    @Override
    public Optional<ReviewEntity> mapToEntity(final Review review) {

        return ofNullable(review).map(
                domain -> ReviewEntity.builder()
                        .reviewId(domain.getReviewId())
                        .sku(domain.getSku())
                        .authorName(domain.getAuthorName())
                        .rating(domain.getRating())
                        .comment(domain.getComment())
                        .status(domain.getStatus())
                        .createdDate(domain.getCreated())
                        .build());
    }

    @Override
    public Optional<Review> mapToDomainObject(final ReviewEntity entity) {

        return ofNullable(entity).map(
                reviewEntity -> Review.builder()
                        .reviewId(reviewEntity.getReviewId())
                        .sku(reviewEntity.getSku())
                        .authorName(reviewEntity.getAuthorName())
                        .rating(reviewEntity.getRating())
                        .comment(reviewEntity.getComment())
                        .status(reviewEntity.getStatus())
                        .created(reviewEntity.getCreatedDate())
                        .build());
    }

}
