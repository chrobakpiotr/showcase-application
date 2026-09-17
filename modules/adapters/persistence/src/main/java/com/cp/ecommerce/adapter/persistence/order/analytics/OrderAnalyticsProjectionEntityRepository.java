package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link OrderAnalyticsProjectionEntity} objects.
 */
@Repository
public interface OrderAnalyticsProjectionEntityRepository extends JpaRepository<OrderAnalyticsProjectionEntity, Long> {

    /**
     * Finds projections most recently consumed first. The caller uses {@code pageable}'s size only (always requesting page 0)
     * to cap how many rows come back, rather than to paginate a stable result set.
     *
     * @param pageable page 0 of the requested size, sorted by consumed date descending.
     * @return matching page of {@link OrderAnalyticsProjectionEntity}.
     */
    Page<OrderAnalyticsProjectionEntity> findAllByOrderByConsumedDateDesc(Pageable pageable);

    /**
     * Counts projections whose {@code orderPlacedDate} falls within the given range (inclusive), for the ops-analytics
     * assistant (see ADR 0021).
     *
     * @param from start of the range (inclusive).
     * @param to end of the range (inclusive).
     * @return matching projection count.
     */
    long countByOrderPlacedDateBetween(Date from, Date to);

}
