package com.cp.ecommerce.domain.review.port.outgoing;

/**
 * Outgoing port for generating a new, unique review id.
 */
public interface GenerateReviewIdOutPort {

    String generate();

}
