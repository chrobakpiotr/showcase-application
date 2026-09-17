package com.cp.ecommerce.adapter.amqp.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SendOrderMessageOutPort} with queuing mechanism disabled.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotSendOrderMessageAdapter implements SendOrderMessageOutPort {

    @Override
    public void send(final Order order) {

        log.info("RabbitMQ disabled for order, messages will not be sent.");
    }

}
