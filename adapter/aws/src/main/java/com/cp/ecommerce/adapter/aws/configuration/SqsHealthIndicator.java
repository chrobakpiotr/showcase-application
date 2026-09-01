package com.cp.ecommerce.adapter.aws.configuration;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Reports SQS connectivity for {@code /actuator/health}. Only registered when the SQS client itself is (see
 * {@code service.aws.sqs.enabled} on {@link AwsClientConfiguration#sqsClient()}).
 *
 * <p>
 * Uses {@code getQueueAttributes} against the single configured queue rather than {@code listQueues}: it needs only
 * {@code sqs:GetQueueAttributes} scoped to that one queue instead of the account-wide {@code sqs:ListQueues} permission,
 * matching this app's actual least-privilege IAM footprint.
 * </p>
 */
@Component("sqs")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.aws.sqs.enabled", havingValue = "true")
public class SqsHealthIndicator implements HealthIndicator {

    private final SqsClient sqsClient;

    private final AwsProperties awsProperties;

    @Override
    public Health health() {

        final String queueUrl = awsProperties.getSqs().getQueueUrl();
        try {
            sqsClient.getQueueAttributes(
                    GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(List.of(QueueAttributeName.QUEUE_ARN))
                            .build());
            return Health.up().withDetail("queueUrl", queueUrl).build();
        } catch (final RuntimeException e) {
            log.warn("SQS health check failed for queue '{}'", queueUrl, e);
            return Health.down(e).withDetail("queueUrl", queueUrl).build();
        }
    }

}
