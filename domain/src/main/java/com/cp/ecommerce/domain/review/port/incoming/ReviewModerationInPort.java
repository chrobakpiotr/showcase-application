package com.cp.ecommerce.domain.review.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.review.Review;

/**
 * Incoming port for the back-office moderation queue: reviewing, approving and rejecting submitted reviews.
 */
public interface ReviewModerationInPort {

    /**
     * Lists every review still awaiting moderation, oldest first.
     */
    List<Review> listPendingReviews();

    /**
     * Approves a review, making it visible via {@link GetProductReviewsInPort} and counted in its
     * {@link com.cp.ecommerce.domain.review.ReviewSummary}. Idempotent: approving an already-approved (or previously rejected)
     * review simply (re)sets its status, mirroring the idempotent-mutation convention established for
     * {@code cart.ManageCartInPort} (ADR 0027). Returns {@code null} if {@code reviewId} does not exist.
     */
    Review approveReview(String reviewId);

    /**
     * Rejects a review, permanently excluding it from customer-facing results. Idempotent, same reasoning as
     * {@link #approveReview}. Returns {@code null} if {@code reviewId} does not exist.
     */
    Review rejectReview(String reviewId);

}
