package com.cp.ecommerce.domain.review.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewSummary;

/**
 * Incoming port for customer-facing, read-only review queries against a single SKU.
 */
public interface GetProductReviewsInPort {

    /**
     * Lists every {@link com.cp.ecommerce.domain.review.ReviewStatus#APPROVED} review for {@code sku}, newest first. Never
     * {@code null}: a SKU with no approved reviews yet is simply represented as an empty list, mirroring
     * {@code inventory.GetStockLevelInPort}'s never-404s stance (ADR 0026) rather than {@code cart.GetCartInPort}'s.
     */
    List<Review> listApprovedReviews(String sku);

    /**
     * Returns the aggregate rating for {@code sku}, computed from approved reviews only. Never {@code null}: a SKU with no
     * approved reviews yet is represented as a zero-average, zero-count {@link ReviewSummary}.
     */
    ReviewSummary getSummary(String sku);

}
