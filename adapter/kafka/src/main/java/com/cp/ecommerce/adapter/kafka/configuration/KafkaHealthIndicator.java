package com.cp.ecommerce.adapter.kafka.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports Kafka broker connectivity for {@code /actuator/health}.
 *
 * <p>
 * Unlike RabbitMQ, Spring Boot 4's Kafka starter ({@code spring-boot-kafka}) does not ship a built-in {@link HealthIndicator}
 * (verified: no such class exists on the classpath, unlike {@code RabbitHealthContributorAutoConfiguration} for AMQP), so this
 * fills that gap.
 * </p>
 *
 * <p>
 * {@link KafkaAdmin#clusterId()} is used as the connectivity probe: it performs a real {@code describeCluster} admin-client
 * call against the configured bootstrap servers and throws on any failure (timeout, unreachable brokers, auth failure), which
 * is exactly the "is Kafka actually reachable" signal a health check needs - cheaper than describing/creating topics, and it
 * doesn't require any topic to already exist.
 * </p>
 */
@Component("kafka")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "true")
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {

        try {
            final String clusterId = kafkaAdmin.clusterId();
            return Health.up().withDetail("clusterId", clusterId).build();
        } catch (final RuntimeException e) {
            log.warn("Kafka health check failed", e);
            return Health.down(e).build();
        }
    }

}
