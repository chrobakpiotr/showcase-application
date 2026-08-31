package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;

/**
 * Incoming port for listing orders page by page.
 */
public interface ListOrdersInPort {

    /**
     * List orders, ordered by creation date descending (most recent first).
     *
     * @param pageQuery requested page and page size.
     * @return {@link PagedResult} of {@link Order}.
     */
    PagedResult<Order> listOrders(final PageQuery pageQuery);

}
