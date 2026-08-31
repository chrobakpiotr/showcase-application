package com.cp.ecommerce.domain.order.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.incoming.FindRecentOrderAnalyticsInPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrderAnalyticsProjectionsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for reading the most recent order-analytics projections.
 */
@UseCase
@RequiredArgsConstructor
public class FindRecentOrderAnalyticsUseCase implements FindRecentOrderAnalyticsInPort {

    private final FindRecentOrderAnalyticsProjectionsOutPort findRecentOrderAnalyticsProjectionsOutPort;

    @Override
    public List<OrderAnalyticsProjection> findRecent(final int limit) {

        return findRecentOrderAnalyticsProjectionsOutPort.findRecent(limit);
    }

}
