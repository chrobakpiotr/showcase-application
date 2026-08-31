package com.cp.ecommerce.adapter.kafka.order;

import java.time.Instant;
import java.util.Date;

import com.cp.ecommerce.adapter.kafka.order.dto.OrderAnalyticsEvent;
import com.cp.ecommerce.adapter.kafka.order.metrics.OrderAnalyticsConsumerMetrics;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.incoming.RecordOrderAnalyticsProjectionInPort;
import com.google.gson.Gson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.kafka.configuration.KafkaTopicConfiguration.ORDER_ANALYTICS_TOPIC_NAME;

/**
 * Consumes order-analytics events from Kafka and records them into the read-model projection queryable via
 * {@code GET /api/order/analytics/recent}, completing the pipeline that {@code PublishOrderAnalyticsEventAdapter} was, until
 * now, only ever the producer side of.
 *
 * <p>
 * Uses the simpler, annotation-driven {@code @KafkaListener} rather than the manual {@code SimpleMessageListenerContainer}
 * wiring {@code MessagingConfiguration} (adapter:amqp) uses for RabbitMQ - a deliberate stylistic contrast, made possible
 * because {@code spring-boot-starter-kafka}'s autoconfiguration already provides a ready-to-use listener container factory,
 * unlike Spring AMQP.
 *
 * <p>
 * Gated behind the same {@code service.kafka.enabled} flag as the producer: unlike an outgoing port (which always needs some
 * bean implementing it, hence {@code DoNotPublishOrderAnalyticsEventAdapter}), nothing depends on this consumer's existence, so
 * when Kafka is disabled it is simply absent instead of falling back to a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "true")
public class OrderAnalyticsEventConsumer {

    private final RecordOrderAnalyticsProjectionInPort recordOrderAnalyticsProjectionInPort;

    private final OrderAnalyticsConsumerMetrics orderAnalyticsConsumerMetrics;

    private final Gson gson;

    @KafkaListener(topics = ORDER_ANALYTICS_TOPIC_NAME)
    public void consume(final String payload) {

        final OrderAnalyticsEvent event = gson.fromJson(payload, OrderAnalyticsEvent.class);
        warnIfUnexpectedSchemaVersion(event);
        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                event.orderNumber(),
                event.customerId(),
                Date.from(Instant.parse(event.timestamp())),
                new Date());
        recordOrderAnalyticsProjectionInPort.recordProjection(projection);
        orderAnalyticsConsumerMetrics.recordConsumed();
        log.info(
                "Consumed order analytics event from Kafka: topic={}, orderNumber={}",
                ORDER_ANALYTICS_TOPIC_NAME,
                event.orderNumber());
    }

    // Tolerated rather than rejected: a future producer schema bump should not stop this demonstration consumer from
    // recording what it still understands, it should just be visible in the logs.
    private void warnIfUnexpectedSchemaVersion(final OrderAnalyticsEvent event) {

        if (!OrderAnalyticsEvent.SCHEMA_VERSION.equals(event.schemaVersion())) {

            log.warn(
                    "Received order analytics event with unexpected schema version '{}' (expected '{}'), processing it "
                            + "anyway.",
                    event.schemaVersion(),
                    OrderAnalyticsEvent.SCHEMA_VERSION);
        }
    }

}
