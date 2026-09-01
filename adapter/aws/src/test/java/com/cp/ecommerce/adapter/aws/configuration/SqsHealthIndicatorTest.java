package com.cp.ecommerce.adapter.aws.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.health.contributor.Status;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link SqsHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class SqsHealthIndicatorTest {

    private static final String QUEUE_URL = "https://sqs.eu-central-1.amazonaws.com/123456789012/order-audit-queue";

    @Mock
    transient SqsClient sqsClient;

    @Test
    void shouldReportUpWhenGetQueueAttributesSucceeds() {

        final AwsProperties awsProperties = awsPropertiesFor(QUEUE_URL);
        final SqsHealthIndicator indicator = new SqsHealthIndicator(sqsClient, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("queueUrl", QUEUE_URL);
    }

    @Test
    void shouldReportDownWhenGetQueueAttributesFails() {

        final AwsProperties awsProperties = awsPropertiesFor(QUEUE_URL);
        given(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .willThrow(QueueDoesNotExistException.builder().build());
        final SqsHealthIndicator indicator = new SqsHealthIndicator(sqsClient, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("queueUrl", QUEUE_URL);
    }

    private static AwsProperties awsPropertiesFor(final String queueUrl) {

        final AwsProperties awsProperties = new AwsProperties();
        awsProperties.getSqs().setQueueUrl(queueUrl);
        return awsProperties;
    }

}
