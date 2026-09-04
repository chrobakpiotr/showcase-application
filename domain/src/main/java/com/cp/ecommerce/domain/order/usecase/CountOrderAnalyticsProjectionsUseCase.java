package com.cp.ecommerce.domain.order.usecase;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.outgoing.CountOrderAnalyticsProjectionsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for counting order-analytics projections within a placement-date range.
 */
@UseCase
@RequiredArgsConstructor
public class CountOrderAnalyticsProjectionsUseCase implements CountOrderAnalyticsProjectionsInPort {

    private final CountOrderAnalyticsProjectionsOutPort countOrderAnalyticsProjectionsOutPort;

    @Override
    public long countPlacedBetween(final Date from, final Date to) {

        return countOrderAnalyticsProjectionsOutPort.countPlacedBetween(from, to);
    }

}
