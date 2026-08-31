package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

/**
 * Persist an {@link OrderAnalyticsProjection} outgoing port.
 */
public interface SaveOrderAnalyticsProjectionOutPort {

    /**
     * Persists a projection row derived from a consumed order-analytics event.
     *
     * @param projection projection to persist.
     */
    void save(OrderAnalyticsProjection projection);

}
