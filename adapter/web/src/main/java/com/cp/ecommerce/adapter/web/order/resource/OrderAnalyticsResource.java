package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;

import lombok.Builder;

/**
 * Resource representing a single row of the order-analytics read model, returned by {@code GET /api/order/analytics/recent}.
 */
@Builder
public record OrderAnalyticsResource(String orderNumber, Long customerId, Date orderPlacedDate, Date consumedDate) {

}
