package com.cp.ecommerce.adapter.web.review.mapper;

import com.cp.ecommerce.adapter.common.utils.ReviewBuilder;
import com.cp.ecommerce.domain.review.Review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ReviewWebMapper}.
 */
class ReviewWebMapperTest {

    private final transient ReviewWebMapper reviewWebMapper = new ReviewWebMapper();

    @Test
    void shouldMapToResource() {

        final Review review = ReviewBuilder.mockReview();

        final var result = reviewWebMapper.mapToResource(review);

        assertTrue(result.isPresent());
        assertEquals(review.getReviewId(), result.get().reviewId());
        assertEquals(review.getSku(), result.get().sku());
        assertEquals(review.getAuthorName(), result.get().authorName());
        assertEquals(review.getRating(), result.get().rating());
        assertEquals(review.getComment(), result.get().comment());
        assertEquals(review.getStatus().name(), result.get().status());
        assertEquals(review.getCreated(), result.get().created());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(reviewWebMapper.mapToResource(null).isEmpty());
    }

}
