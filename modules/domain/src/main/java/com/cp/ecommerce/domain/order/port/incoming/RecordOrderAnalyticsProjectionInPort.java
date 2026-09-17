package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

/**
 * Incoming port for recording a consumed order-analytics event into the read-model projection.
 */
public interface RecordOrderAnalyticsProjectionInPort {

    /**
     * Records a projection row derived from a consumed order-analytics event.
     *
     * @param projection projection to record.
     */
    void recordProjection(OrderAnalyticsProjection projection);

}
