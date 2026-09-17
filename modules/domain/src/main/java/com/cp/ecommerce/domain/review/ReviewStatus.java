package com.cp.ecommerce.domain.review;

/**
 * Moderation lifecycle status of a {@link Review}.
 */
public enum ReviewStatus {

    /**
     * Submitted but not yet moderated; this is the default/initial status. Not visible on product listings until approved.
     */
    PENDING,

    /**
     * Approved by an operator - visible in {@code GetProductReviewsInPort} results and counted in {@link ReviewSummary}.
     */
    APPROVED,

    /**
     * Rejected by an operator - never surfaced to customers, kept only for moderation audit history.
     */
    REJECTED

}
