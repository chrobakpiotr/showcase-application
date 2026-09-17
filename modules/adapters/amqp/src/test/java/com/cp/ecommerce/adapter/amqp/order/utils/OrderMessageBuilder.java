package com.cp.ecommerce.adapter.amqp.order.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.cp.ecommerce.domain.order.OrderMessage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link OrderMessage} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderMessageBuilder {

    public static OrderMessage mockOrderMessage() throws ParseException {

        return OrderMessage.builder()
                .schemaVersion("0.9")
                .created(new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).parse("2023-02-20"))
                .customerId(1111L)
                .orderNumber("number")
                .build();
    }

}
