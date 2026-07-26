package com.cp.ecommerce.adapter.aws.configuration;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Provides AWS SDK v2 client beans for S3, SQS, and Secrets Manager. Each bean is individually gated by its own
 * {@code service.aws.*.enabled} property so that only the resources actually in use are instantiated.
 */
@Configuration
@RequiredArgsConstructor
public class AwsClientConfiguration {

    private final AwsProperties awsProperties;

    /**
     * S3 client pointing at LocalStack (or real AWS when {@code aws.endpoint-override} is blank).
     */
    @Bean
    @ConditionalOnProperty(name = "service.aws.s3.enabled", havingValue = "true")
    public S3Client s3Client() {

        return configure(S3Client.builder()).forcePathStyle(true).build();
    }

    /**
     * SQS client pointing at LocalStack (or real AWS).
     */
    @Bean
    @ConditionalOnProperty(name = "service.aws.sqs.enabled", havingValue = "true")
    public SqsClient sqsClient() {

        return configure(SqsClient.builder()).build();
    }

    /**
     * Secrets Manager client pointing at LocalStack (or real AWS).
     */
    @Bean
    @ConditionalOnProperty(name = "service.aws.secretsmanager.enabled", havingValue = "true")
    public SecretsManagerClient secretsManagerClient() {

        return configure(SecretsManagerClient.builder()).build();
    }

    /**
     * Applies the common endpoint/region/credentials/httpClient configuration shared by all AWS SDK v2 client builders.
     */
    private <B extends AwsClientBuilder<B, C> & SdkSyncClientBuilder<B, C>, C> B configure(final B builder) {

        return builder.endpointOverride(URI.create(awsProperties.getEndpointOverride()))
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(staticCredentials())
                .httpClient(UrlConnectionHttpClient.create());
    }

    private StaticCredentialsProvider staticCredentials() {

        return StaticCredentialsProvider
                .create(AwsBasicCredentials.create(awsProperties.getAccessKeyId(), awsProperties.getSecretAccessKey()));
    }

}
