package com.cp.ecommerce.domain.review.usecase;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.outgoing.GenerateReviewIdOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link SubmitReviewUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SubmitReviewUseCaseTest {

    private static final String REVIEW_ID = "REVIEW-1";

    private static final String SKU = "SKU-1";

    @InjectMocks
    private transient SubmitReviewUseCase submitReviewUseCase;

    @Mock
    private transient GenerateReviewIdOutPort generateReviewIdOutPort;

    @Mock
    private transient SaveReviewOutPort saveReviewOutPort;

    @Test
    void shouldSubmitReviewWithGeneratedIdAndPendingStatus() {

        given(generateReviewIdOutPort.generate()).willReturn(REVIEW_ID);
        given(saveReviewOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Review result = submitReviewUseCase.submitReview(SKU, "Jane Smith", 4, "Solid product.");

        assertThat(result.getReviewId()).isEqualTo(REVIEW_ID);
        assertThat(result.getSku()).isEqualTo(SKU);
        assertThat(result.getAuthorName()).isEqualTo("Jane Smith");
        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getComment()).isEqualTo("Solid product.");
        assertThat(result.getStatus().name()).isEqualTo("PENDING");
    }

}
