package com.cp.ecommerce.domain.review.port.outgoing;

import com.cp.ecommerce.domain.review.ReviewSummary;

/**
 * Outgoing port for computing a SKU's aggregate rating from its approved reviews - delegated to the persistence layer (a SQL
 * {@code AVG}/{@code COUNT}) rather than loading every review into memory to average client-side.
 */
public interface ComputeReviewSummaryOutPort {

    ReviewSummary compute(String sku);

}
