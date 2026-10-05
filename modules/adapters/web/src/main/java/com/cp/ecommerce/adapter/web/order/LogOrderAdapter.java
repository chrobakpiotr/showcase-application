package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Output port for logging a bounded summary of a placed {@link Order}.
 */
@Slf4j
@WebAdapter
public class LogOrderAdapter implements LogOrderOutPort {

    public void log(final Order order) {
        final OrderStatus status;
        final int itemCount;
        try {
            status = order.getStatus();
            itemCount = order.getItems().size();
        } catch (RuntimeException exception) {
            log.warn("Unable to create bounded order summary");
            return;
        }

        log.info("Order placed: status={}, itemCount={}", status, itemCount);
    }
}
