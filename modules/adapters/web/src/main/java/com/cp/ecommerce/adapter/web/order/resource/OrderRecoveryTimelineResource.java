package com.cp.ecommerce.adapter.web.order.resource;

import java.util.List;

import lombok.Builder;

/**
 * Bounded page of the read-only order recovery projection.
 */
@Builder
public record OrderRecoveryTimelineResource(String orderNumber, int page, int size,
        List<OrderRecoveryTimelineEntryResource> items) {
}
