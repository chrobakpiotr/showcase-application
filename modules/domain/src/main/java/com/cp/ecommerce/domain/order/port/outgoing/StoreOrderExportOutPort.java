package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for storing a JSON export/archive of a successfully-processed order. Implementations may write to AWS S3, a
 * local filesystem, or any other durable storage.
 */
public interface StoreOrderExportOutPort {

    /**
     * Stores a JSON representation of the given order.
     *
     * @param order {@link Order} object to export.
     */
    void store(Order order);

}
