package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_AUTHOR_NAME;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_COMMENT;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_CREATED;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_ID;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_RATING;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_SKU;
import static com.cp.ecommerce.adapter.common.utils.ReviewBuilder.TEST_REVIEW_STATUS;

/**
 * Builder class for {@link ReviewEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewEntityBuilder {

    public static ReviewEntity mockReviewEntity() {

        return ReviewEntity.builder()
                .reviewId(TEST_REVIEW_ID)
                .sku(TEST_REVIEW_SKU)
                .authorName(TEST_REVIEW_AUTHOR_NAME)
                .rating(TEST_REVIEW_RATING)
                .comment(TEST_REVIEW_COMMENT)
                .status(TEST_REVIEW_STATUS)
                .createdDate(TEST_REVIEW_CREATED)
                .build();
    }

}
