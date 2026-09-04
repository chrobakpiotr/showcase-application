package com.cp.ecommerce.domain.review.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewSummary;
import com.cp.ecommerce.domain.review.port.incoming.GetProductReviewsInPort;
import com.cp.ecommerce.domain.review.port.outgoing.ComputeReviewSummaryOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindApprovedReviewsBySkuOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for customer-facing, read-only product review queries.
 */
@UseCase
@RequiredArgsConstructor
public class GetProductReviewsUseCase implements GetProductReviewsInPort {

    private final FindApprovedReviewsBySkuOutPort findApprovedReviewsBySkuOutPort;

    private final ComputeReviewSummaryOutPort computeReviewSummaryOutPort;

    @Override
    public List<Review> listApprovedReviews(final String sku) {

        return findApprovedReviewsBySkuOutPort.findApprovedBySku(sku);
    }

    @Override
    public ReviewSummary getSummary(final String sku) {

        return computeReviewSummaryOutPort.compute(sku);
    }

}
