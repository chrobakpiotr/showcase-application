package com.cp.ecommerce.adapter.kafka.configuration;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuration class for the Kafka order-analytics event stream.
 *
 * <p>
 * Declaring the {@link NewTopic} bean lets Spring Boot's auto-configured {@code KafkaAdmin} create the topic on startup, the
 * same way {@code MessagingConfiguration} declares the AMQP queue/exchange/binding for RabbitMQ.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "true")
public class KafkaTopicConfiguration {

    public static final String ORDER_ANALYTICS_TOPIC_NAME = "com.cp.e.topic.order.analytics";

    // Matches DeadLetterPublishingRecoverer's own default naming convention ("<topic>-dlt"), so
    // KafkaErrorHandlingConfiguration doesn't need a custom destination resolver.
    public static final String ORDER_ANALYTICS_DEAD_LETTER_TOPIC_NAME = ORDER_ANALYTICS_TOPIC_NAME + "-dlt";

    private static final int PARTITION_COUNT = 3;

    private static final short REPLICATION_FACTOR = 1;

    @Bean
    NewTopic orderAnalyticsTopic() {

        return TopicBuilder.name(ORDER_ANALYTICS_TOPIC_NAME).partitions(PARTITION_COUNT).replicas(REPLICATION_FACTOR).build();
    }

    // Explicitly provisioned rather than relying on broker auto-topic-creation (same reasoning as the main topic
    // above), and with the same partition count as the main topic: DeadLetterPublishingRecoverer publishes to the
    // *same partition number* the failed record came from, so the DLT needs at least as many partitions.
    @Bean
    NewTopic orderAnalyticsDeadLetterTopic() {

        return TopicBuilder.name(ORDER_ANALYTICS_DEAD_LETTER_TOPIC_NAME)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

}
