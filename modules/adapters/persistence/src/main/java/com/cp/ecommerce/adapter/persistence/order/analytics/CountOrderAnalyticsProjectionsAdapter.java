package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.time.Instant;

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
    public long countPlacedBetween(final Instant from, final Instant to) {

        return orderAnalyticsProjectionEntityRepository.countByOrderPlacedDateBetween(from, to);
    }

}
