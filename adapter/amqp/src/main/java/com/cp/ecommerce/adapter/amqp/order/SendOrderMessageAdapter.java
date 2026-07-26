package com.cp.ecommerce.adapter.amqp.order;

import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;
import com.google.gson.Gson;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.ROUTING_KEY;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.TOPIC_EXCHANGE_NAME;

/**
 * Implementation of {@link SendOrderMessageOutPort} functionality.
 */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.rabbitmq.enabled", havingValue = "true")
public class SendOrderMessageAdapter implements SendOrderMessageOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "sendOrderMessage";

    private final OrderMessageMapper mapper;

    private final RabbitTemplate rabbitTemplate;

    private final ResilientExecutor resilientExecutor;

    private final Gson gson;

    public void send(final Order order) {

        final OrderMessage orderMessage = mapper.mapToMessage(order)
                .orElseThrow(() -> new IllegalStateException("Failed to map order to message: " + order.getOrderNumber()));
        log.info("Message: {}", orderMessage);
        resilientExecutor.runResilient(
                RESILIENCE_INSTANCE_NAME,
                () -> rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, ROUTING_KEY, gson.toJson(orderMessage)));
    }

}
