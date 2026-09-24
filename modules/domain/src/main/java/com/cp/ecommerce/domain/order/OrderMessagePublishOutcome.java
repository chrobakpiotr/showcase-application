package com.cp.ecommerce.domain.order;

/**
 * Result of executing one logical fulfillment-publish boundary.
 *
 * <p>
 * ACCEPTED means broker acknowledgement and no mandatory return. It does not mean the consumer committed its local business
 * effect. UNKNOWN means acceptance cannot be inferred safely, so replay must reuse the same operation identity.
 * </p>
 */
public enum OrderMessagePublishOutcome {
    ACCEPTED,
    REJECTED,
    UNKNOWN
}
