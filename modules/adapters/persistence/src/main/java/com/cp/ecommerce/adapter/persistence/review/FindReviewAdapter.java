package com.cp.ecommerce.adapter.persistence.review;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.mapper.ReviewPersistenceMapper;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.outgoing.FindReviewOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindReviewOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindReviewAdapter implements FindReviewOutPort {

    private final ReviewEntityRepository reviewEntityRepository;

    private final ReviewPersistenceMapper reviewPersistenceMapper;

    @Override
    public Review find(final String reviewId) {

        return reviewEntityRepository.findById(reviewId).flatMap(reviewPersistenceMapper::mapToDomainObject).orElse(null);
    }

}
