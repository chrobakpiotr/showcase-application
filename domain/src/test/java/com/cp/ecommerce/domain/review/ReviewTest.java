package com.cp.ecommerce.domain.review;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Review}.
 */
class ReviewTest {

    @Test
    void shouldPassValidationForValidReview() {

        final Review review = TestDomainObjectFactory.validReview();

        assertDoesNotThrow(review::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultStatusToPending() {

        final Review review = Review.builder().sku("SKU-1").authorName("Jane").rating(4).comment("Good.").build();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void shouldFailValidationWhenSkuIsBlank() {

        final Review review = Review.builder().sku(" ").authorName("Jane").rating(4).comment("Good.").build();

        assertThrows(DomainObjectValidationException.class, review::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenAuthorNameIsBlank() {

        final Review review = Review.builder().sku("SKU-1").authorName(" ").rating(4).comment("Good.").build();

        assertThrows(DomainObjectValidationException.class, review::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenCommentIsBlank() {

        final Review review = Review.builder().sku("SKU-1").authorName("Jane").rating(4).comment(" ").build();

        assertThrows(DomainObjectValidationException.class, review::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenRatingIsBelowMinimum() {

        final Review review = Review.builder().sku("SKU-1").authorName("Jane").rating(0).comment("Good.").build();

        assertThrows(DomainObjectValidationException.class, review::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenRatingIsAboveMaximum() {

        final Review review = Review.builder().sku("SKU-1").authorName("Jane").rating(6).comment("Good.").build();

        assertThrows(DomainObjectValidationException.class, review::assertValidationsEmpty);
    }

}
