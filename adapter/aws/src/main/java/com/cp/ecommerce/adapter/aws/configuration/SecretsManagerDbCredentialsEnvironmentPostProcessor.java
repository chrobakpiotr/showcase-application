package com.cp.ecommerce.adapter.aws.configuration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * {@link EnvironmentPostProcessor} that fetches datasource credentials from AWS Secrets Manager and injects them as
 * {@code spring.datasource.username} / {@code spring.datasource.password} into the Spring {@link ConfigurableEnvironment}
 * <em>before</em> the {@code DataSource} bean is created.
 *
 * <p>
 * This processor is <strong>opt-in only</strong>: it does nothing unless {@code service.aws.secretsmanager.enabled=true} is
 * present in the environment (e.g. via the {@code aws-localstack} profile). Any connectivity failure is logged as a warning and
 * the processor falls back to whatever credentials are already configured in application YAML files, so the application always
 * starts - it never blocks startup.
 *
 * <p>
 * <strong>Ordering note:</strong> This processor uses {@code Ordered.LOWEST_PRECEDENCE - 1} so it runs very late in the
 * post-processor chain - well after {@code ConfigDataEnvironmentPostProcessor} (which has order
 * {@code HIGHEST_PRECEDENCE + 10}) has loaded all profile-specific {@code application-{profile}.yml} files. That guarantees the
 * {@code aws.*} properties required to build the Secrets Manager client are already available.
 */
@Slf4j
public class SecretsManagerDbCredentialsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "aws-secretsmanager-db-credentials";

    private static final Gson GSON = new Gson();

    /** Run very late so profile YAML files are already loaded. */
    @Override
    public int getOrder() {

        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {

        final Boolean enabled = environment.getProperty("service.aws.secretsmanager.enabled", Boolean.class, false);
        if (!enabled) {
            return;
        }

        final String endpoint = environment.getProperty("aws.endpoint-override", "http://localhost:4566");
        final String region = environment.getProperty("aws.region", "us-east-1");
        final String accessKey = environment.getProperty("aws.access-key-id", "test");
        final String secretKey = environment.getProperty("aws.secret-access-key", "test");
        final String secretName = environment.getProperty("aws.secretsmanager.secret-name", "");

        try (SecretsManagerClient client = buildSecretsManagerClient(endpoint, region, accessKey, secretKey)) {

            final String secretString = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build())
                    .secretString();

            final DbCredentials credentials = GSON.fromJson(secretString, DbCredentials.class);

            final Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.username", credentials.username);
            props.put("spring.datasource.password", credentials.password);

            // addFirst ensures this source takes precedence over application-*.yml values
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
            log.info("Datasource credentials loaded from Secrets Manager secret: {}", secretName);

        } catch (Exception ex) {

            log.warn(
                    "Could not load datasource credentials from Secrets Manager (secret: {}). "
                            + "Falling back to application.yml credentials. Cause: {}",
                    secretName,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Builds a {@link SecretsManagerClient} from the given parameters. Protected to allow test subclasses to substitute a mock
     * client.
     */
    protected SecretsManagerClient buildSecretsManagerClient(
            final String endpoint,
            final String region,
            final String accessKey,
            final String secretKey) {

        return SecretsManagerClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    /**
     * Local record for deserialising the Secrets Manager JSON payload {@code {"username": "...", "password": "..."}}.
     */
    private static final class DbCredentials {

        @SerializedName("username")
        String username;

        @SerializedName("password")
        String password;

    }

}
