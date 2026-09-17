package com.cp.ecommerce.domain.order.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.DetectDuplicateOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrdersByCustomerOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for a best-effort AI-assisted check of whether an order is a likely accidental duplicate of one of the same
 * customer's other recent orders.
 *
 * <p>
 * Short-circuits without ever calling {@link DetectDuplicateOrderOutPort} when there is nothing to compare against (no recent
 * orders from the same customer) - the trivial, always-correct answer in that case is "not a duplicate", so there is no reason
 * to pay for a model call the AI-backed adapter would have to make the same trivial decision anyway.
 */
@RequiredArgsConstructor
@UseCase
public class DetectDuplicateOrderUseCase implements DetectDuplicateOrderInPort {

    private final FindRecentOrdersByCustomerOutPort findRecentOrdersByCustomerOutPort;

    private final DetectDuplicateOrderOutPort detectDuplicateOrderOutPort;

    @Override
    public DuplicateOrderCheckResult detectDuplicate(final Order order) {

        final List<Order> recentOrders = findRecentOrdersByCustomerOutPort.findRecentOrders(order);
        if (recentOrders.isEmpty()) {
            return DuplicateOrderCheckResult.none();
        }
        return detectDuplicateOrderOutPort.check(order, recentOrders);
    }

}
