package com.cp.ecommerce.adapter.kafka.order.metrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Component recording metrics related to consuming order-analytics events from Kafka, exposed via Micrometer to the configured
 * registries (e.g. Prometheus). Kept local to adapter:kafka rather than reusing adapter:web's {@code OrderMetrics}, since
 * adapters must not depend on one another in this hexagonal layout.
 * <p>
 * Also implements {@link RetryListener} so that {@code KafkaErrorHandlingConfiguration}'s error handler can report retry/
 * dead-letter activity here, keeping every observability signal for this consumer in one place.
 */
@Slf4j
@Component
public class OrderAnalyticsConsumerMetrics implements RetryListener {

    private static final String ORDER_ANALYTICS_CONSUMED_METRIC_NAME = "orders.analytics.consumed";

    private static final String ORDER_ANALYTICS_DEAD_LETTERED_METRIC_NAME = "orders.analytics.dead_lettered";

    private final transient Counter orderAnalyticsConsumedCounter;

    private final transient Counter orderAnalyticsDeadLetteredCounter;

    public OrderAnalyticsConsumerMetrics(final MeterRegistry meterRegistry) {

        this.orderAnalyticsConsumedCounter = Counter.builder(ORDER_ANALYTICS_CONSUMED_METRIC_NAME)
                .description("Number of order-analytics events consumed from Kafka and recorded into the read model")
                .register(meterRegistry);
        this.orderAnalyticsDeadLetteredCounter = Counter.builder(ORDER_ANALYTICS_DEAD_LETTERED_METRIC_NAME)
                .description(
                        "Number of order-analytics events that exhausted all retries and were published to the dead-letter "
                                + "topic")
                .register(meterRegistry);
    }

    public void recordConsumed() {

        orderAnalyticsConsumedCounter.increment();
    }

    @Override
    public void failedDelivery(final ConsumerRecord<?, ?> record, final Exception exception, final int deliveryAttempt) {

        log.warn(
                "Failed to process order-analytics Kafka record (partition={}, offset={}) on delivery attempt {}: {}",
                record.partition(),
                record.offset(),
                deliveryAttempt,
                exception.getMessage());
    }

    @Override
    public void recovered(final ConsumerRecord<?, ?> record, final Exception exception) {

        orderAnalyticsDeadLetteredCounter.increment();
        log.error(
                "Order-analytics Kafka record (partition={}, offset={}) exhausted all retries and was published to the "
                        + "dead-letter topic: {}",
                record.partition(),
                record.offset(),
                exception.getMessage());
    }

    @Override
    public void recoveryFailed(final ConsumerRecord<?, ?> record, final Exception original, final Exception failure) {

        log.error(
                "Order-analytics Kafka record (partition={}, offset={}) failed AND could not be published to the "
                        + "dead-letter topic - it is lost: {}",
                record.partition(),
                record.offset(),
                failure.getMessage());
    }

}
