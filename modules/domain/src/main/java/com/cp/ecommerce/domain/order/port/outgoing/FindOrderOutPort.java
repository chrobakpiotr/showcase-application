package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Find order from order number outgoing port.
 */
public interface FindOrderOutPort {

    /**
     * Find order by order number.
     *
     * @param orderNumber order number.
     * @return {@link Order} object.
     */
    Order find(final String orderNumber);
}
