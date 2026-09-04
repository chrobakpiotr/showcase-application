package com.cp.ecommerce.domain.review.port.outgoing;

import com.cp.ecommerce.domain.review.Review;

/**
 * Outgoing port for looking up a single review by its id, used by {@code ReviewModerationInPort}'s approve/reject flows.
 */
public interface FindReviewOutPort {

    /**
     * Returns the review for {@code reviewId}, or {@code null} if no such review exists.
     */
    Review find(String reviewId);

}
