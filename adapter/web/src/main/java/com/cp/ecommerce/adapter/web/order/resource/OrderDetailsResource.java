package com.cp.ecommerce.adapter.web.order.resource;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing the full order details returned by {@code GET /api/order/{orderNumber}}. Deliberately excludes internal
 * identifiers (e.g. the customer's persistence id) that are implementation details, not part of the public API contract.
 */
@Builder
public record OrderDetailsResource(@Schema(example = "a343b57f-f1b0-46c4-846c-f8ee538f30f0-3") String orderNumber,
        @Schema(example = "CONFIRMED") OrderStatus status, @Schema(example = "2024-03-15T10:30:00.000Z") Date created,
        @Schema(example = "Please leave the package with the concierge.") String remarks, CustomerResource customer,
        List<OrderLineItemResource> items, @Schema(example = "59.98") BigDecimal total,
        @Schema(example = "CARD") PaymentMethod paymentMethod, PaymentResource payment) {

}
