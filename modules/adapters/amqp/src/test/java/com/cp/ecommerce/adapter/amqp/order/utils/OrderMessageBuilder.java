package com.cp.ecommerce.adapter.amqp.order.utils;

import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderMessage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link OrderMessage} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderMessageBuilder {

    public static OrderMessage mockOrderMessage() {

        return OrderMessage.builder()
                .schemaVersion("0.9")
                .created(Instant.parse("2023-02-20T00:00:00Z"))
                .customerId(1111L)
                .orderNumber("number")
                .build();
    }

}
