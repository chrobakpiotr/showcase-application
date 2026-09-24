package com.cp.ecommerce.domain.order.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;

/**
 * Read-only order recovery timeline use-case boundary.
 */
public interface FindOrderRecoveryTimelineInPort {

    List<OrderRecoveryTimelineEntry> find(String orderNumber, int page, int size);
}
