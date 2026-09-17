package com.cp.ecommerce.adapter.persistence.review;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateReviewIdAdapter}.
 */
class GenerateReviewIdAdapterTest {

    private final transient GenerateReviewIdAdapter generateReviewIdAdapter = new GenerateReviewIdAdapter();

    @Test
    void shouldGenerateReviewIdWithExpectedPrefix() {

        final String reviewId = generateReviewIdAdapter.generate();

        assertTrue(reviewId.startsWith("REVIEW-"));
    }

    @Test
    void shouldGenerateUniqueReviewIdOnEachCall() {

        assertNotEquals(generateReviewIdAdapter.generate(), generateReviewIdAdapter.generate());
    }

}
