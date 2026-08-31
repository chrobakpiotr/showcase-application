package com.cp.ecommerce.adapter.persistence.order.analytics;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderAnalyticsProjectionOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveOrderAnalyticsProjectionOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveOrderAnalyticsProjectionAdapter implements SaveOrderAnalyticsProjectionOutPort {

    private final OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    private final OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Override
    public void save(final OrderAnalyticsProjection projection) {

        orderAnalyticsProjectionPersistenceMapper.mapToEntity(projection)
                .ifPresent(orderAnalyticsProjectionEntityRepository::save);
    }

}
