package com.cp.ecommerce.adapter.persistence.review.entity;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.review.ReviewStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ReviewEntity}.
 */
public interface ReviewEntityRepository extends JpaRepository<ReviewEntity, String> {

    List<ReviewEntity> findBySkuAndStatusOrderByCreatedDateDesc(String sku, ReviewStatus status);

    List<ReviewEntity> findByStatusOrderByCreatedDateAsc(ReviewStatus status);

    long countBySkuAndStatus(String sku, ReviewStatus status);

    /**
     * Average rating across every review for {@code sku} with the given {@code status}, computed in the database rather than in
     * application code (see {@code ComputeReviewSummaryAdapter}), mirroring
     * {@code OrderAnalyticsProjectionEntityRepository.countByOrderPlacedDateBetween}'s aggregate-query precedent.
     *
     * @return the average rating, or {@code null} if there are no matching reviews.
     */
    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.sku = :sku AND r.status = :status")
    Optional<Double> averageRatingBySkuAndStatus(@Param("sku") String sku, @Param("status") ReviewStatus status);

}
