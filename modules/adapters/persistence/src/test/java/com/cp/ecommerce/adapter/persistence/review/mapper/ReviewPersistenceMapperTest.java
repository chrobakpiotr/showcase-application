package com.cp.ecommerce.adapter.persistence.review.mapper;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.adapter.persistence.utils.ReviewEntityBuilder;
import com.cp.ecommerce.domain.review.Review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ReviewPersistenceMapper}.
 */
class ReviewPersistenceMapperTest {

    private final transient ReviewPersistenceMapper reviewPersistenceMapper = new ReviewPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final Review review = ReviewBuilder.mockReview();

        final var result = reviewPersistenceMapper.mapToEntity(review);

        assertTrue(result.isPresent());
        assertEquals(review.getReviewId(), result.get().getReviewId());
        assertEquals(review.getSku(), result.get().getSku());
        assertEquals(review.getAuthorName(), result.get().getAuthorName());
        assertEquals(review.getRating(), result.get().getRating());
        assertEquals(review.getComment(), result.get().getComment());
        assertEquals(review.getStatus(), result.get().getStatus());
        assertEquals(review.getCreated(), result.get().getCreatedDate());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = ReviewEntityBuilder.mockReviewEntity();

        final var result = reviewPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getReviewId(), result.get().getReviewId());
        assertEquals(entity.getSku(), result.get().getSku());
        assertEquals(entity.getAuthorName(), result.get().getAuthorName());
        assertEquals(entity.getRating(), result.get().getRating());
        assertEquals(entity.getComment(), result.get().getComment());
        assertEquals(entity.getStatus(), result.get().getStatus());
        assertEquals(entity.getCreatedDate(), result.get().getCreated());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(reviewPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(reviewPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
