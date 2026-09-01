package com.cp.ecommerce.adapter.aws.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Reports S3 connectivity for {@code /actuator/health}. Only registered when the S3 client itself is (see
 * {@code service.aws.s3.enabled} on {@link AwsClientConfiguration#s3Client()}), so this never shows up as a false-negative
 * "down" component when S3 export is intentionally disabled.
 *
 * <p>
 * Uses {@code headBucket} against the single configured bucket rather than {@code listBuckets}: it needs only
 * {@code s3:ListBucket}/{@code s3:HeadBucket} scoped to that one bucket instead of the account-wide {@code s3:ListAllMyBuckets}
 * permission, matching this app's actual least-privilege IAM footprint.
 * </p>
 */
@Component("s3")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.aws.s3.enabled", havingValue = "true")
public class S3HealthIndicator implements HealthIndicator {

    private final S3Client s3Client;

    private final AwsProperties awsProperties;

    @Override
    public Health health() {

        final String bucketName = awsProperties.getS3().getBucketName();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return Health.up().withDetail("bucketName", bucketName).build();
        } catch (final RuntimeException e) {
            log.warn("S3 health check failed for bucket '{}'", bucketName, e);
            return Health.down(e).withDetail("bucketName", bucketName).build();
        }
    }

}
