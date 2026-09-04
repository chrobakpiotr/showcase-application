package com.cp.ecommerce.domain.order.port.incoming;

import java.util.Date;

/**
 * Incoming port for counting how many order-analytics projections fall within a placement-date range.
 */
public interface CountOrderAnalyticsProjectionsInPort {

    /**
     * Counts projections whose {@code orderPlacedDate} falls within the given range (inclusive).
     *
     * @param from start of the range (inclusive).
     * @param to end of the range (inclusive).
     * @return matching projection count.
     */
    long countPlacedBetween(Date from, Date to);

}
