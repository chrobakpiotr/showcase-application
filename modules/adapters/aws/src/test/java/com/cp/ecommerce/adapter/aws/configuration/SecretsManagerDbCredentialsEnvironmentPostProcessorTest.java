package com.cp.ecommerce.adapter.aws.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SecretsManagerDbCredentialsEnvironmentPostProcessor}.
 */
@ExtendWith(MockitoExtension.class)
class SecretsManagerDbCredentialsEnvironmentPostProcessorTest {

    @Mock
    private transient SecretsManagerClient secretsManagerClient;

    @Mock
    private transient SpringApplication springApplication;

    @Test
    void shouldSkipWhenSecretsManagerDisabled() {

        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty("service.aws.secretsmanager.enabled", "false");
        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = processorWithMockClient(null);

        processor.postProcessEnvironment(environment, springApplication);

        verify(secretsManagerClient, never()).getSecretValue(any(GetSecretValueRequest.class));
        assertThat(environment.getPropertySources().contains("aws-secretsmanager-db-credentials")).isFalse();
    }

    @Test
    void shouldSkipWhenSecretsManagerNotConfigured() {

        final MockEnvironment environment = new MockEnvironment();
        // service.aws.secretsmanager.enabled not set => defaults to false
        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = processorWithMockClient(null);

        processor.postProcessEnvironment(environment, springApplication);

        verify(secretsManagerClient, never()).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    void shouldInjectCredentialsWhenEnabled() {

        final MockEnvironment environment = buildEnabledEnvironment();
        final String secretJson = new Gson().toJson(new TestSecret("myuser", "mypass"));
        given(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .willReturn(GetSecretValueResponse.builder().secretString(secretJson).build());
        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = processorWithMockClient(secretsManagerClient);

        processor.postProcessEnvironment(environment, springApplication);

        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("myuser");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("mypass");
        assertThat(environment.getPropertySources().contains("aws-secretsmanager-db-credentials")).isTrue();
    }

    @Test
    void shouldPutCredentialsPropertySourceFirst() {

        final MockEnvironment environment = buildEnabledEnvironment();
        environment.setProperty("spring.datasource.username", "yaml-user");
        final String secretJson = new Gson().toJson(new TestSecret("secret-user", "secret-pass"));
        given(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .willReturn(GetSecretValueResponse.builder().secretString(secretJson).build());
        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = processorWithMockClient(secretsManagerClient);

        processor.postProcessEnvironment(environment, springApplication);

        // Secrets Manager value must take precedence (addFirst)
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("secret-user");
    }

    @Test
    void shouldNotThrowWhenSecretsManagerFails() {

        final MockEnvironment environment = buildEnabledEnvironment();
        given(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .willThrow(new RuntimeException("LocalStack unavailable"));
        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = processorWithMockClient(secretsManagerClient);

        // Must not rethrow - just logs a warning and continues
        processor.postProcessEnvironment(environment, springApplication);

        assertThat(environment.getPropertySources().contains("aws-secretsmanager-db-credentials")).isFalse();
    }

    @Test
    void shouldImplementCurrentSpringBootEnvironmentPostProcessor() {

        assertThat(EnvironmentPostProcessor.class).isAssignableFrom(SecretsManagerDbCredentialsEnvironmentPostProcessor.class);
    }

    @Test
    void shouldBeRegisteredUnderCurrentSpringBootEnvironmentPostProcessorKey() throws IOException {

        final String expected = SecretsManagerDbCredentialsEnvironmentPostProcessor.class.getName();
        final Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/spring.factories");

        boolean registered = false;
        while (resources.hasMoreElements() && !registered) {
            final Properties factories = new Properties();
            try (InputStream input = resources.nextElement().openStream()) {
                factories.load(input);
            }
            final String implementations = factories.getProperty(EnvironmentPostProcessor.class.getName(), "");
            registered = implementations.lines()
                    .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                    .map(String::trim)
                    .anyMatch(expected::equals);
        }

        assertThat(registered).isTrue();
    }

    @Test
    void shouldHaveCorrectOrder() {

        final SecretsManagerDbCredentialsEnvironmentPostProcessor processor = new SecretsManagerDbCredentialsEnvironmentPostProcessor();

        assertThat(processor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 1);
    }

    private MockEnvironment buildEnabledEnvironment() {

        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty("service.aws.secretsmanager.enabled", "true");
        environment.setProperty("aws.endpoint-override", "http://localhost:4566");
        environment.setProperty("aws.region", "us-east-1");
        environment.setProperty("aws.access-key-id", "test");
        environment.setProperty("aws.secret-access-key", "test");
        environment.setProperty("aws.secretsmanager.secret-name", "ecommerce/db-credentials");
        return environment;
    }

    /**
     * Returns a subclass of the processor that always returns the given mock client, bypassing real AWS SDK builder
     * construction.
     */
    private SecretsManagerDbCredentialsEnvironmentPostProcessor processorWithMockClient(final SecretsManagerClient mockClient) {

        return new SecretsManagerDbCredentialsEnvironmentPostProcessor() {

            @Override
            protected SecretsManagerClient buildSecretsManagerClient(
                    final String endpoint,
                    final String region,
                    final String accessKey,
                    final String secretKey) {

                return mockClient != null ? mockClient : secretsManagerClient;
            }
        };
    }

    /** Simple holder for test JSON serialisation. */
    private static class TestSecret {

        final String username;
        final String password;

        TestSecret(final String username, final String password) {

            this.username = username;
            this.password = password;
        }

    }

}
