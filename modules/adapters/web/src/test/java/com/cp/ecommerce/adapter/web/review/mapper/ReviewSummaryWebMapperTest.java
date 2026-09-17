package com.cp.ecommerce.adapter.web.review.mapper;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.review.ReviewSummary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ReviewSummaryWebMapper}.
 */
class ReviewSummaryWebMapperTest {

    private final transient ReviewSummaryWebMapper reviewSummaryWebMapper = new ReviewSummaryWebMapper();

    @Test
    void shouldMapToResource() {

        final ReviewSummary summary = new ReviewSummary("SKU-1", BigDecimal.valueOf(4.5), 3L);

        final var result = reviewSummaryWebMapper.mapToResource(summary);

        assertTrue(result.isPresent());
        assertEquals(summary.sku(), result.get().sku());
        assertEquals(summary.averageRating(), result.get().averageRating());
        assertEquals(summary.reviewCount(), result.get().reviewCount());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(reviewSummaryWebMapper.mapToResource(null).isEmpty());
    }

}
