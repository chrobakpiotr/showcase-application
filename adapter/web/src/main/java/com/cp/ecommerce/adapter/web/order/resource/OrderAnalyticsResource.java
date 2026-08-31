package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a single row of the order-analytics read model, returned by {@code GET /api/order/analytics/recent}.
 */
@Builder
public record OrderAnalyticsResource(@Schema(example = "a343b57f-f1b0-46c4-846c-f8ee538f30f0-3") String orderNumber,
        @Schema(example = "3") Long customerId, @Schema(example = "2024-03-15T10:30:00.000Z") Date orderPlacedDate,
        @Schema(example = "2024-03-15T10:30:02.500Z") Date consumedDate) {

}
