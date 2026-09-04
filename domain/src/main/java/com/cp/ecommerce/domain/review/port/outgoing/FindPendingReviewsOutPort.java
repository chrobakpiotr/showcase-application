package com.cp.ecommerce.domain.review.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.review.Review;

/**
 * Outgoing port for listing every review still awaiting moderation, oldest first.
 */
public interface FindPendingReviewsOutPort {

    List<Review> findPending();

}
