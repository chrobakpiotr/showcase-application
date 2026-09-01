package com.cp.ecommerce.adapter.kafka.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link KafkaHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    private static final String CLUSTER_ID = "test-cluster-id";

    @Mock
    transient KafkaAdmin kafkaAdmin;

    @Test
    void shouldReportUpWhenClusterIdIsResolved() {

        given(kafkaAdmin.clusterId()).willReturn(CLUSTER_ID);
        final KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterId", CLUSTER_ID);
    }

    @Test
    void shouldReportDownWhenClusterIdCannotBeResolved() {

        given(kafkaAdmin.clusterId()).willThrow(new KafkaException("broker unreachable"));
        final KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

}
