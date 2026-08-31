package com.cp.ecommerce.adapter.persistence.order.analytics;

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

}
