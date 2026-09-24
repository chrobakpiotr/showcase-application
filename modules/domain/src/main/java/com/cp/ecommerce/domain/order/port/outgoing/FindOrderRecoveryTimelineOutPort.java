package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;

/**
 * Reads a bounded page of the durable order recovery projection.
 */
public interface FindOrderRecoveryTimelineOutPort {

    List<OrderRecoveryTimelineEntry> find(String orderNumber, int page, int size);
}
