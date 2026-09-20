package com.cp.ecommerce.domain.review.usecase;

import java.time.Instant;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.outgoing.FindPendingReviewsOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindReviewOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.GenerateReviewIdOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewUseCasesMutationWave2Test {

    @Mock
    private FindReviewOutPort findReviewOutPort;
    @Mock
    private FindPendingReviewsOutPort findPendingReviewsOutPort;
    @Mock
    private GenerateReviewIdOutPort generateReviewIdOutPort;
    @Mock
    private SaveReviewOutPort saveReviewOutPort;

    @Test
    void moderationShouldValidateRebuiltReviewBeforeSave() {
        final Review invalidExisting = Review.builder()
                .reviewId("REVIEW-1")
                .sku("SKU-1")
                .authorName("Jane")
                .rating(4)
                .comment(" ")
                .created(Instant.EPOCH)
                .build();
        given(findReviewOutPort.find("REVIEW-1")).willReturn(invalidExisting);
        final ReviewModerationUseCase useCase = new ReviewModerationUseCase(
                findReviewOutPort,
                findPendingReviewsOutPort,
                saveReviewOutPort);

        assertThatThrownBy(() -> useCase.approveReview("REVIEW-1")).isInstanceOf(DomainObjectValidationException.class);
        verify(saveReviewOutPort, never()).save(any());
    }

    @Test
    void submissionShouldValidateReviewBeforeSave() {
        given(generateReviewIdOutPort.generate()).willReturn("REVIEW-1");
        final SubmitReviewUseCase useCase = new SubmitReviewUseCase(generateReviewIdOutPort, saveReviewOutPort);

        assertThatThrownBy(() -> useCase.submitReview("SKU-1", " ", 4, "comment"))
                .isInstanceOf(DomainObjectValidationException.class);
        verify(saveReviewOutPort, never()).save(any());
    }
}
