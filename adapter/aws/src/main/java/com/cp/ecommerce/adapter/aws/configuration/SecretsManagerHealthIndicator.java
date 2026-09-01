package com.cp.ecommerce.adapter.aws.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;

/**
 * Reports Secrets Manager connectivity for {@code /actuator/health}. Only registered when the Secrets Manager client itself is
 * (see {@code service.aws.secretsmanager.enabled} on {@link AwsClientConfiguration#secretsManagerClient()}).
 *
 * <p>
 * Uses {@code describeSecret} against the single configured secret rather than {@code listSecrets}: it needs only
 * {@code secretsmanager:DescribeSecret} scoped to that one secret instead of the account-wide
 * {@code secretsmanager:ListSecrets} permission, and - unlike {@code getSecretValue} - never returns the actual secret
 * material, so it can't leak credentials into a health-check code path or its logs.
 * </p>
 */
@Component("secretsManager")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.aws.secretsmanager.enabled", havingValue = "true")
public class SecretsManagerHealthIndicator implements HealthIndicator {

    private final SecretsManagerClient secretsManagerClient;

    private final AwsProperties awsProperties;

    @Override
    public Health health() {

        final String secretName = awsProperties.getSecretsmanager().getSecretName();
        try {
            secretsManagerClient.describeSecret(DescribeSecretRequest.builder().secretId(secretName).build());
            return Health.up().withDetail("secretName", secretName).build();
        } catch (final RuntimeException e) {
            log.warn("Secrets Manager health check failed for secret '{}'", secretName, e);
            return Health.down(e).withDetail("secretName", secretName).build();
        }
    }

}
