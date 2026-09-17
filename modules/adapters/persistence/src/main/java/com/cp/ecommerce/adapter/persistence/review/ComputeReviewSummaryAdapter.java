package com.cp.ecommerce.adapter.persistence.review;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.review.ReviewSummary;
import com.cp.ecommerce.domain.review.port.outgoing.ComputeReviewSummaryOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ComputeReviewSummaryOutPort} - delegates the average/count computation to the database rather than
 * loading every approved review into memory.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class ComputeReviewSummaryAdapter implements ComputeReviewSummaryOutPort {

    private static final int AVERAGE_SCALE = 1;

    private final ReviewEntityRepository reviewEntityRepository;

    @Override
    public ReviewSummary compute(final String sku) {

        final long reviewCount = reviewEntityRepository.countBySkuAndStatus(sku, ReviewStatus.APPROVED);
        final BigDecimal averageRating = reviewEntityRepository.averageRatingBySkuAndStatus(sku, ReviewStatus.APPROVED)
                .map(average -> BigDecimal.valueOf(average).setScale(AVERAGE_SCALE, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
        return new ReviewSummary(sku, averageRating, reviewCount);
    }

}
