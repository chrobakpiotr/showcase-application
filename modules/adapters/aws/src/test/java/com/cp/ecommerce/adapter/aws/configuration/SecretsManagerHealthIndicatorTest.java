package com.cp.ecommerce.adapter.aws.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.health.contributor.Status;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link SecretsManagerHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class SecretsManagerHealthIndicatorTest {

    private static final String SECRET_NAME = "ecommerce/datasource";

    @Mock
    transient SecretsManagerClient secretsManagerClient;

    @Test
    void shouldReportUpWhenDescribeSecretSucceeds() {

        final AwsProperties awsProperties = awsPropertiesFor(SECRET_NAME);
        final SecretsManagerHealthIndicator indicator = new SecretsManagerHealthIndicator(secretsManagerClient, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("secretName", SECRET_NAME);
    }

    @Test
    void shouldReportDownWhenDescribeSecretFails() {

        final AwsProperties awsProperties = awsPropertiesFor(SECRET_NAME);
        given(secretsManagerClient.describeSecret(any(DescribeSecretRequest.class)))
                .willThrow(ResourceNotFoundException.builder().build());
        final SecretsManagerHealthIndicator indicator = new SecretsManagerHealthIndicator(secretsManagerClient, awsProperties);

        final var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("secretName", SECRET_NAME);
    }

    private static AwsProperties awsPropertiesFor(final String secretName) {

        final AwsProperties awsProperties = new AwsProperties();
        awsProperties.getSecretsmanager().setSecretName(secretName);
        return awsProperties;
    }

}
