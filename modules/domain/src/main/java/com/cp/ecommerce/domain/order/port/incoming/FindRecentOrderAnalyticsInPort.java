package com.cp.ecommerce.domain.order.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

/**
 * Incoming port for reading the most recent order-analytics projections.
 */
public interface FindRecentOrderAnalyticsInPort {

    /**
     * Finds the most recently consumed projections, ordered by consumption date descending (most recent first).
     *
     * @param limit maximum number of projections to return.
     * @return recent {@link OrderAnalyticsProjection} rows, most recently consumed first.
     */
    List<OrderAnalyticsProjection> findRecent(int limit);

}
