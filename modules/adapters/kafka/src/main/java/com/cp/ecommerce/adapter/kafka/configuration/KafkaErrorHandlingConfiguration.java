package com.cp.ecommerce.adapter.kafka.configuration;

import org.apache.kafka.common.TopicPartition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

import static com.cp.ecommerce.adapter.kafka.configuration.KafkaTopicConfiguration.ORDER_ANALYTICS_DEAD_LETTER_TOPIC_NAME;

/**
 * Configures {@code @KafkaListener} error handling for the whole application (currently only {@code
 * OrderAnalyticsEventConsumer}): without this, Spring Kafka's own built-in fallback silently retries a failing record 9 times
 * with no delay and then only logs and skips it - there is no visibility beyond that log line, and no way to later inspect or
 * replay what failed.
 * <p>
 * Boot only wires a {@link DefaultErrorHandler} bean into the auto-configured listener container factory if exactly one such
 * bean is present (see {@code KafkaAnnotationDrivenConfiguration}), so - consistent with this project's general preference for
 * using Spring Boot's autoconfiguration rather than hand-building it (see the contrast with {@code MessagingConfiguration}'s
 * manual AMQP wiring, documented on {@code OrderAnalyticsEventConsumer}) - only the missing error handler needs to be supplied
 * here; the container factory itself remains auto-configured.
 */
@Configuration
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "true")
public class KafkaErrorHandlingConfiguration {

    // Retries are intentionally few and immediate: this consumer's failures are almost always a malformed payload
    // (permanent, retrying won't help) rather than a transient broker hiccup (already handled by the Kafka client
    // itself before a record is even delivered to the listener), so a handful of retries mainly guards against the
    // rare transient projection-write failure before giving up and dead-lettering.
    private static final long BACKOFF_INTERVAL_MS = 1000L;

    private static final long MAX_RETRY_ATTEMPTS = 3L;

    @Bean
    DefaultErrorHandler kafkaConsumerErrorHandler(
            final KafkaTemplate<String, String> kafkaTemplate,
            final RetryListener retryListener) {

        // Destination is spelled out explicitly (rather than relying on DeadLetterPublishingRecoverer's own default
        // "<topic>-dlt" naming) so this stays in lock-step with the NewTopic bean KafkaTopicConfiguration actually
        // provisions, instead of two independently-hardcoded "-dlt" suffixes coincidentally matching.
        final DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(ORDER_ANALYTICS_DEAD_LETTER_TOPIC_NAME, record.partition()));
        final DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(BACKOFF_INTERVAL_MS, MAX_RETRY_ATTEMPTS));
        errorHandler.setRetryListeners(retryListener);
        return errorHandler;
    }

}
