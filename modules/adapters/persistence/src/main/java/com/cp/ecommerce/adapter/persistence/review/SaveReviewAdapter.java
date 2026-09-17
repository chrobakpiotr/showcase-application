package com.cp.ecommerce.adapter.persistence.review;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.mapper.ReviewPersistenceMapper;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.port.outgoing.SaveReviewOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveReviewOutPort}.
 *
 * <p>
 * Unlike {@code SaveCartAdapter}, this has no optimistic-locking/version column: moderation actions (see
 * {@code ReviewModerationUseCase}) are unconditional status overwrites performed one operator at a time, so the concurrent-
 * conflict scenario {@code CartConflictException} guards against does not apply here.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveReviewAdapter implements SaveReviewOutPort {

    private final ReviewEntityRepository reviewEntityRepository;

    private final ReviewPersistenceMapper reviewPersistenceMapper;

    @Override
    public Review save(final Review review) {

        final ReviewEntity entityToSave = reviewPersistenceMapper.mapToEntity(review)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map review domain object to entity for id: " + review.getReviewId()));
        final ReviewEntity saved = reviewEntityRepository.save(entityToSave);
        return reviewPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map review entity to domain object for id: " + review.getReviewId()));
    }

}
