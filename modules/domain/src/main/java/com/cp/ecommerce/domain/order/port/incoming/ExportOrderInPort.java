package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for triggering an order export to durable storage.
 */
public interface ExportOrderInPort {

    /**
     * Exports the given order to the configured durable storage.
     *
     * @param order {@link Order} to export.
     */
    void exportOrder(Order order);

}
