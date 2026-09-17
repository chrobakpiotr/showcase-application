package com.cp.ecommerce.domain.review.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.review.port.incoming.ReviewModerationInPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindPendingReviewsOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.FindReviewOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for the back-office review moderation queue.
 */
@UseCase
@RequiredArgsConstructor
public class ReviewModerationUseCase implements ReviewModerationInPort {

    private final FindReviewOutPort findReviewOutPort;

    private final FindPendingReviewsOutPort findPendingReviewsOutPort;

    private final SaveReviewOutPort saveReviewOutPort;

    @Override
    public List<Review> listPendingReviews() {

        return findPendingReviewsOutPort.findPending();
    }

    @Override
    public Review approveReview(final String reviewId) {

        return transitionStatus(reviewId, ReviewStatus.APPROVED);
    }

    @Override
    public Review rejectReview(final String reviewId) {

        return transitionStatus(reviewId, ReviewStatus.REJECTED);
    }

    private Review transitionStatus(final String reviewId, final ReviewStatus status) {

        final Review existing = findReviewOutPort.find(reviewId);
        if (existing == null) {

            return null;
        }
        final Review moderated = Review.builder()
                .reviewId(existing.getReviewId())
                .sku(existing.getSku())
                .authorName(existing.getAuthorName())
                .rating(existing.getRating())
                .comment(existing.getComment())
                .status(status)
                .created(existing.getCreated())
                .build();
        moderated.assertValidationsEmpty();
        return saveReviewOutPort.save(moderated);
    }

}
