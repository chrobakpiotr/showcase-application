package com.cp.ecommerce.domain.review.usecase;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.incoming.SubmitReviewInPort;
import com.cp.ecommerce.domain.review.port.outgoing.GenerateReviewIdOutPort;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for a customer submitting a new product review.
 */
@UseCase
@RequiredArgsConstructor
public class SubmitReviewUseCase implements SubmitReviewInPort {

    private final GenerateReviewIdOutPort generateReviewIdOutPort;

    private final SaveReviewOutPort saveReviewOutPort;

    @Override
    public Review submitReview(final String sku, final String authorName, final int rating, final String comment) {

        final Review review = Review.builder()
                .reviewId(generateReviewIdOutPort.generate())
                .sku(sku)
                .authorName(authorName)
                .rating(rating)
                .comment(comment)
                .created(new Date())
                .build();
        review.assertValidationsEmpty();
        return saveReviewOutPort.save(review);
    }

}
