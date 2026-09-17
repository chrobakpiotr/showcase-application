package com.cp.ecommerce.adapter.persistence.review;

import java.math.BigDecimal;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.review.ReviewSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link ComputeReviewSummaryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class ComputeReviewSummaryAdapterTest {

    private static final String SKU = "SKU-1";

    @InjectMocks
    private transient ComputeReviewSummaryAdapter computeReviewSummaryAdapter;

    @Mock
    private transient ReviewEntityRepository reviewEntityRepository;

    @Test
    void shouldComputeSummaryWithApprovedReviews() {

        doReturn(2L).when(reviewEntityRepository).countBySkuAndStatus(SKU, ReviewStatus.APPROVED);
        doReturn(Optional.of(4.5)).when(reviewEntityRepository).averageRatingBySkuAndStatus(SKU, ReviewStatus.APPROVED);

        final ReviewSummary result = computeReviewSummaryAdapter.compute(SKU);

        assertEquals(new ReviewSummary(SKU, BigDecimal.valueOf(4.5), 2L), result);
    }

    @Test
    void shouldReturnZeroAverageWhenNoApprovedReviewsExist() {

        doReturn(0L).when(reviewEntityRepository).countBySkuAndStatus(SKU, ReviewStatus.APPROVED);
        doReturn(Optional.empty()).when(reviewEntityRepository).averageRatingBySkuAndStatus(SKU, ReviewStatus.APPROVED);

        final ReviewSummary result = computeReviewSummaryAdapter.compute(SKU);

        assertEquals(new ReviewSummary(SKU, BigDecimal.ZERO, 0L), result);
    }

}
