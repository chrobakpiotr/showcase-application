package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

/**
 * Find the most recently consumed order-analytics projections outgoing port.
 */
public interface FindRecentOrderAnalyticsProjectionsOutPort {

    /**
     * Finds the most recently consumed projections, ordered by consumption date descending (most recent first).
     *
     * @param limit maximum number of projections to return.
     * @return recent {@link OrderAnalyticsProjection} rows, most recently consumed first.
     */
    List<OrderAnalyticsProjection> findRecent(int limit);

}
