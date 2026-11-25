package com.example.eventflow.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Creates the S3 client only when app.storage.type=s3. Credentials come from the AWS default provider
 * chain (EC2 instance role, or AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY environment variables) - they are
 * never read from application files.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(
            @Value("${app.storage.s3.region:}") String region,
            @Value("${app.storage.s3.endpoint:}") String endpoint) {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("app.storage.s3.region (env AWS_REGION) must be set when app.storage.type=s3");
        }
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            // optional: S3-compatible endpoint such as LocalStack / MinIO for local experiments
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }
}
