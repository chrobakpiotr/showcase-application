package com.cp.ecommerce.adapter.aws.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.health.contributor.Status;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link S3HealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {

    private static final String BUCKET_NAME = "order-export-bucket";

    @Mock
    transient S3Client s3Client;

    @Test
    void shouldReportUpWhenHeadBucketSucceeds() {

        final AwsProperties awsProperties = awsPropertiesFor(BUCKET_NAME);
        final S3HealthIndicator indicator = new S3HealthIndicator(s3Client, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucketName", BUCKET_NAME);
    }

    @Test
    void shouldReportDownWhenHeadBucketFails() {

        final AwsProperties awsProperties = awsPropertiesFor(BUCKET_NAME);
        given(s3Client.headBucket(any(HeadBucketRequest.class))).willThrow(NoSuchBucketException.builder().build());
        final S3HealthIndicator indicator = new S3HealthIndicator(s3Client, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("bucketName", BUCKET_NAME);
    }

    private static AwsProperties awsPropertiesFor(final String bucketName) {

        final AwsProperties awsProperties = new AwsProperties();
        awsProperties.getS3().setBucketName(bucketName);
        return awsProperties;
    }

}
