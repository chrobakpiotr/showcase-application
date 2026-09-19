package com.cp.ecommerce.domain.order.usecase;

import java.time.Instant;

import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.outgoing.CountOrderAnalyticsProjectionsOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for counting order-analytics projections within a placement-date range.
 */
@UseCase
@RequiredArgsConstructor
public class CountOrderAnalyticsProjectionsUseCase implements CountOrderAnalyticsProjectionsInPort {

    private final CountOrderAnalyticsProjectionsOutPort countOrderAnalyticsProjectionsOutPort;

    @Override
    public long countPlacedBetween(final Instant from, final Instant to) {

        return countOrderAnalyticsProjectionsOutPort.countPlacedBetween(from, to);
    }

}
