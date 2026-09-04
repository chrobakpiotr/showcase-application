package com.cp.ecommerce.adapter.persistence.review;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.review.port.outgoing.GenerateReviewIdOutPort;

/**
 * Implementation of {@link GenerateReviewIdOutPort}, mirroring {@code GenerateCartIdAdapter}: a pure random UUID is sufficient
 * to guarantee uniqueness, no dialect-specific sequence needed.
 */
@PersistenceAdapter
class GenerateReviewIdAdapter implements GenerateReviewIdOutPort {

    private static final String REVIEW_ID_PREFIX = "REVIEW-";

    @Override
    public String generate() {

        return REVIEW_ID_PREFIX + UUID.randomUUID();
    }

}
