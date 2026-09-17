package com.cp.ecommerce.adapter.aws.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for AWS / LocalStack connectivity and resource coordinates. Bound from the {@code aws.*} namespace
 * in application properties.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aws")
@Component
public class AwsProperties {

    /** AWS region (defaults to {@code us-east-1}). */
    private String region = "us-east-1";

    /** LocalStack (or real AWS) endpoint override URL, e.g. {@code http://localhost:4566}. */
    private String endpointOverride;

    /** AWS access key ID (LocalStack convention: {@code test}). */
    private String accessKeyId = "test";

    /** AWS secret access key (LocalStack convention: {@code test}). */
    private String secretAccessKey = "test";

    private S3Properties s3 = new S3Properties();

    private SqsProperties sqs = new SqsProperties();

    private SecretsManagerProperties secretsmanager = new SecretsManagerProperties();

    /**
     * S3-specific properties.
     */
    @Getter
    @Setter
    public static class S3Properties {

        /** Name of the S3 bucket where order JSON exports are stored. */
        private String bucketName;
    }

    /**
     * SQS-specific properties.
     */
    @Getter
    @Setter
    public static class SqsProperties {

        /** Full URL of the SQS queue for order audit events. */
        private String queueUrl;
    }

    /**
     * Secrets Manager-specific properties.
     */
    @Getter
    @Setter
    public static class SecretsManagerProperties {

        /** Name / ARN of the Secrets Manager secret containing the datasource credentials. */
        private String secretName;
    }

}
