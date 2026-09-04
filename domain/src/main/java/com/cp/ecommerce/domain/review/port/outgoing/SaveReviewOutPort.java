package com.cp.ecommerce.domain.review.port.outgoing;

import com.cp.ecommerce.domain.review.Review;

/**
 * Outgoing port for persisting a {@link Review}, whether newly submitted or moderated (status transition).
 */
public interface SaveReviewOutPort {

    Review save(Review review);

}
