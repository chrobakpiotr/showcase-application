package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.CountOrderAnalyticsProjectionsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CountOrderAnalyticsProjectionsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class CountOrderAnalyticsProjectionsAdapter implements CountOrderAnalyticsProjectionsOutPort {

    private final OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Override
    public long countPlacedBetween(final Date from, final Date to) {

        return orderAnalyticsProjectionEntityRepository.countByOrderPlacedDateBetween(from, to);
    }

}
