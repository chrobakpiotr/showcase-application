package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Save order outgoing port.
 */
public interface SaveOrderOutPort {

    /**
     * This method is meant to trigger all steps to save order to the database.
     *
     * @param order {@link Order} object.
     * @return saved order object.
     */
    Order save(final Order order);
}
