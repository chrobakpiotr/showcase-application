package com.cp.ecommerce.adapter.kafka.order;

import java.time.Instant;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.kafka.order.dto.OrderAnalyticsEvent;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAnalyticsEventOutPort;
import com.google.gson.Gson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.kafka.configuration.KafkaTopicConfiguration.ORDER_ANALYTICS_TOPIC_NAME;

/**
 * Implementation of {@link PublishOrderAnalyticsEventOutPort} that publishes an order-placed event to Kafka, wrapped behind a
 * circuit-breaker and retry. Keyed by order number so that all events for a given order land on the same partition and are
 * therefore consumed in order by any downstream analytics consumer.
 */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "true")
public class PublishOrderAnalyticsEventAdapter implements PublishOrderAnalyticsEventOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "publishOrderAnalyticsEvent";

    private static final String EVENT_TYPE = "ORDER_PLACED";

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ResilientExecutor resilientExecutor;

    private final Gson gson;

    @Override
    public void publish(final Order order) {

        final OrderAnalyticsEvent event = OrderAnalyticsEvent.builder()
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomer().getId())
                .eventType(EVENT_TYPE)
                .timestamp(Instant.now().toString())
                .build();
        final String json = gson.toJson(event);
        log.info(
                "Publishing order analytics event to Kafka: topic={}, orderNumber={}",
                ORDER_ANALYTICS_TOPIC_NAME,
                order.getOrderNumber());
        resilientExecutor.runResilient(
                RESILIENCE_INSTANCE_NAME,
                () -> kafkaTemplate.send(ORDER_ANALYTICS_TOPIC_NAME, order.getOrderNumber(), json));
    }

}
