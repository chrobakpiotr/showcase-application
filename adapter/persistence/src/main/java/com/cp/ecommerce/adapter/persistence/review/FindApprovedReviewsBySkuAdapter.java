package com.cp.ecommerce.adapter.persistence.review;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntityRepository;
import com.cp.ecommerce.adapter.persistence.review.mapper.ReviewPersistenceMapper;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.review.port.outgoing.FindApprovedReviewsBySkuOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindApprovedReviewsBySkuOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindApprovedReviewsBySkuAdapter implements FindApprovedReviewsBySkuOutPort {

    private final ReviewEntityRepository reviewEntityRepository;

    private final ReviewPersistenceMapper reviewPersistenceMapper;

    @Override
    public List<Review> findApprovedBySku(final String sku) {

        return reviewEntityRepository.findBySkuAndStatusOrderByCreatedDateDesc(sku, ReviewStatus.APPROVED)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    private Review mapToDomainObjectOrThrow(final ReviewEntity entity) {

        return reviewPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map review entity to domain object for id: " + entity.getReviewId()));
    }

}
