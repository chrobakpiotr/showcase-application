package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;

import com.cp.ecommerce.domain.order.OrderStatus;

import lombok.Builder;

/**
 * Resource representing the full order details returned by {@code GET /api/order/{orderNumber}}. Deliberately excludes internal
 * identifiers (e.g. the customer's persistence id) that are implementation details, not part of the public API contract.
 */
@Builder
public record OrderDetailsResource(String orderNumber, OrderStatus status, Date created, String remarks,
        CustomerResource customer) {

}
