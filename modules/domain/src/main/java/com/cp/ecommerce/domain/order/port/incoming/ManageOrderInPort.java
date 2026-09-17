package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Manage order data incoming port.
 */
public interface ManageOrderInPort {

    /**
     * This method is meant to trigger all steps to save order data to the database.
     *
     * @param order {@link Order} object.
     */
    Order saveOrder(final Order order);

    /**
     * Find order by order number.
     *
     * @param orderNumber number of order to be found.
     * @return {@link Order} object if exists.
     */
    Order findOrder(final String orderNumber);

}
