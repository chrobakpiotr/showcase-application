package com.cp.ecommerce.domain.review.usecase;

import java.util.List;

import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.review.port.outgoing.FindPendingReviewsOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindReviewOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ReviewModerationUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ReviewModerationUseCaseTest {

    private static final String REVIEW_ID = "REVIEW-1";

    @InjectMocks
    private transient ReviewModerationUseCase reviewModerationUseCase;

    @Mock
    private transient FindReviewOutPort findReviewOutPort;

    @Mock
    private transient FindPendingReviewsOutPort findPendingReviewsOutPort;

    @Mock
    private transient SaveReviewOutPort saveReviewOutPort;

    @Test
    void shouldListPendingReviews() {

        final List<Review> pending = List.of(TestDomainObjectFactory.validReview());
        given(findPendingReviewsOutPort.findPending()).willReturn(pending);

        final List<Review> result = reviewModerationUseCase.listPendingReviews();

        assertThat(result).isEqualTo(pending);
    }

    @Test
    void shouldApproveExistingReview() {

        given(findReviewOutPort.find(REVIEW_ID)).willReturn(TestDomainObjectFactory.validReview());
        given(saveReviewOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Review result = reviewModerationUseCase.approveReview(REVIEW_ID);

        assertThat(result.getStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void shouldRejectExistingReview() {

        given(findReviewOutPort.find(REVIEW_ID)).willReturn(TestDomainObjectFactory.validReview());
        given(saveReviewOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Review result = reviewModerationUseCase.rejectReview(REVIEW_ID);

        assertThat(result.getStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void shouldReturnNullWhenApprovingUnknownReview() {

        given(findReviewOutPort.find(REVIEW_ID)).willReturn(null);

        final Review result = reviewModerationUseCase.approveReview(REVIEW_ID);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenRejectingUnknownReview() {

        given(findReviewOutPort.find(REVIEW_ID)).willReturn(null);

        final Review result = reviewModerationUseCase.rejectReview(REVIEW_ID);

        assertThat(result).isNull();
    }

}
