package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OrderAnalyticsProjectionPersistenceMapper;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrderAnalyticsProjectionsOutPort;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindRecentOrderAnalyticsProjectionsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindRecentOrderAnalyticsProjectionsAdapter implements FindRecentOrderAnalyticsProjectionsOutPort {

    private final OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    private final OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper;

    @Override
    public List<OrderAnalyticsProjection> findRecent(final int limit) {

        final Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "consumedDate"));
        return orderAnalyticsProjectionEntityRepository.findAllByOrderByConsumedDateDesc(pageable)
                .getContent()
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    private OrderAnalyticsProjection mapToDomainObjectOrThrow(final OrderAnalyticsProjectionEntity entity) {

        return orderAnalyticsProjectionPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map order analytics projection entity to domain object for order number: "
                                        + entity.getOrderNumber()));
    }

}
