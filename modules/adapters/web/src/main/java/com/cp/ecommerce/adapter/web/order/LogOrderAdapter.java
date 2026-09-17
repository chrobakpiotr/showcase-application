package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Output port for logging placed {@link Order} in JSON format.
 */
@Slf4j
@RequiredArgsConstructor
@WebAdapter
public class LogOrderAdapter implements LogOrderOutPort {

    private final ObjectMapper objectMapper;

    public void log(final Order order) {

        try {

            log.info("Order's content: \n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(order));
        } catch (JacksonException e) {
            log.warn("Error while parsing order for logging: {}", e.getMessage(), e);
        }
    }

}
