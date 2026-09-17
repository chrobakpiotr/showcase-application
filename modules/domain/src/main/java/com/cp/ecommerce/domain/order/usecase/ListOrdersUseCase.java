package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.port.incoming.ListOrdersInPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrdersOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for listing orders page by page.
 */
@UseCase
@RequiredArgsConstructor
public class ListOrdersUseCase implements ListOrdersInPort {

    private final FindOrdersOutPort findOrdersOutPort;

    @Override
    public PagedResult<Order> listOrders(final PageQuery pageQuery) {

        return findOrdersOutPort.findAll(pageQuery);
    }

}
