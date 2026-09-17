package com.cp.ecommerce.adapter.common.utils;

import java.util.Date;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Review} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewBuilder {

    public static final String TEST_REVIEW_ID = "REVIEW-1234";

    public static final String TEST_REVIEW_SKU = "SKU-1234";

    public static final String TEST_REVIEW_AUTHOR_NAME = "Jane Smith";

    public static final int TEST_REVIEW_RATING = 4;

    public static final String TEST_REVIEW_COMMENT = "Solid product, works as expected.";

    public static final ReviewStatus TEST_REVIEW_STATUS = ReviewStatus.PENDING;

    public static final Date TEST_REVIEW_CREATED = new Date(1710000000000L);

    public static Review mockReview() {

        return Review.builder()
                .reviewId(TEST_REVIEW_ID)
                .sku(TEST_REVIEW_SKU)
                .authorName(TEST_REVIEW_AUTHOR_NAME)
                .rating(TEST_REVIEW_RATING)
                .comment(TEST_REVIEW_COMMENT)
                .status(TEST_REVIEW_STATUS)
                .created(TEST_REVIEW_CREATED)
                .build();
    }

}
