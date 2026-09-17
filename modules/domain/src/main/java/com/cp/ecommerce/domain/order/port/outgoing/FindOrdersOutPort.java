package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;

/**
 * Find a page of orders outgoing port.
 */
public interface FindOrdersOutPort {

    /**
     * Find a page of orders, ordered by creation date descending (most recent first).
     *
     * @param pageQuery requested page and page size.
     * @return {@link PagedResult} of {@link Order}.
     */
    PagedResult<Order> findAll(final PageQuery pageQuery);

}
