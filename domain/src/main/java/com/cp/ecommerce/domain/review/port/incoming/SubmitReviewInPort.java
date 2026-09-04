package com.cp.ecommerce.domain.review.port.incoming;

import com.cp.ecommerce.domain.review.Review;

/**
 * Incoming port for a customer submitting a new product review.
 */
public interface SubmitReviewInPort {

    /**
     * Creates a new review for {@code sku}, starting in {@link com.cp.ecommerce.domain.review.ReviewStatus#PENDING} - it is not
     * visible in {@link GetProductReviewsInPort} results until an operator approves it via {@link ReviewModerationInPort}.
     */
    Review submitReview(String sku, String authorName, int rating, String comment);

}
