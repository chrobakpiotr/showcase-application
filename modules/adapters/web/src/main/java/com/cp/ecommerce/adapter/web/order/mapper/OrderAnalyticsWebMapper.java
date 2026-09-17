package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.order.resource.OrderAnalyticsResource;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link OrderAnalyticsProjection} domain object to its web resource. Read-only: this
 * read-model is only ever populated by the Kafka consumer, never written to via the REST API.
 */
@Component
public class OrderAnalyticsWebMapper implements WebResponseMapper<OrderAnalyticsProjection, OrderAnalyticsResource> {

    @Override
    public Optional<OrderAnalyticsResource> mapToResource(final OrderAnalyticsProjection domainObject) {

        return Optional.ofNullable(domainObject)
                .map(
                        projection -> OrderAnalyticsResource.builder()
                                .orderNumber(projection.orderNumber())
                                .customerId(projection.customerId())
                                .orderPlacedDate(projection.orderPlacedDate())
                                .consumedDate(projection.consumedDate())
                                .build());
    }

}
