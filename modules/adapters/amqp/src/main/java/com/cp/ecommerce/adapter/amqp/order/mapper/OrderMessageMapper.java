package com.cp.ecommerce.adapter.amqp.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.QueueMessageMapper;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Mapper responsible for changing order request to be compatible with order domain.
 */
@Component
@RequiredArgsConstructor
public class OrderMessageMapper implements QueueMessageMapper<Order, OrderMessage> {

    @Override
    public Optional<Order> mapToDomainObject(final OrderMessage message) {

        return Optional.empty();
    }

    @Override
    public Optional<OrderMessage> mapToMessage(final Order domainObject) {

        return Optional.ofNullable(
                OrderMessage.builder()
                        .created(domainObject.getCreated())
                        .customerId(domainObject.getCustomer().getId())
                        .orderNumber(domainObject.getOrderNumber())
                        .build());
    }
}
