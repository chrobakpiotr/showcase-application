package com.cp.ecommerce.adapter.amqp.order;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * AMQP message listener.
 */
@Component
@Slf4j
public class MessageListener {

    public void receiveMessage(final String message) {

        log.info("Received <{}>", message);
    }

}
