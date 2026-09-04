package com.cp.ecommerce.domain.review.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.review.Review;

/**
 * Outgoing port for listing every {@link com.cp.ecommerce.domain.review.ReviewStatus#APPROVED} review of a SKU, newest first.
 */
public interface FindApprovedReviewsBySkuOutPort {

    List<Review> findApprovedBySku(String sku);

}
