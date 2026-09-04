package com.cp.ecommerce.domain.review.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewSummary;
import com.cp.ecommerce.domain.review.port.outgoing.ComputeReviewSummaryOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindApprovedReviewsBySkuOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link GetProductReviewsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetProductReviewsUseCaseTest {

    private static final String SKU = "SKU-1";

    @InjectMocks
    private transient GetProductReviewsUseCase getProductReviewsUseCase;

    @Mock
    private transient FindApprovedReviewsBySkuOutPort findApprovedReviewsBySkuOutPort;

    @Mock
    private transient ComputeReviewSummaryOutPort computeReviewSummaryOutPort;

    @Test
    void shouldListApprovedReviewsForSku() {

        final List<Review> reviews = List.of(TestDomainObjectFactory.validReview());
        given(findApprovedReviewsBySkuOutPort.findApprovedBySku(SKU)).willReturn(reviews);

        final List<Review> result = getProductReviewsUseCase.listApprovedReviews(SKU);

        assertThat(result).isEqualTo(reviews);
    }

    @Test
    void shouldReturnSummaryForSku() {

        final ReviewSummary summary = new ReviewSummary(SKU, BigDecimal.valueOf(4.5), 2L);
        given(computeReviewSummaryOutPort.compute(SKU)).willReturn(summary);

        final ReviewSummary result = getProductReviewsUseCase.getSummary(SKU);

        assertThat(result).isEqualTo(summary);
    }

}
