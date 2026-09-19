package com.cp.ecommerce.domain.order.port.outgoing;

import java.time.Instant;

/**
 * Outgoing port for counting order-analytics projections within a placement-date range.
 */
public interface CountOrderAnalyticsProjectionsOutPort {

    /**
     * Counts projections whose {@code orderPlacedDate} falls within the given range (inclusive).
     *
     * @param from start of the range (inclusive).
     * @param to end of the range (inclusive).
     * @return matching projection count.
     */
    long countPlacedBetween(Instant from, Instant to);

}
