package com.ensemble.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Wires the {@link S3Client} used by {@code S3PhotoStorage}. Only built when
 * {@code ensemble.photos.backend=s3}, so disk-mode local dev never constructs
 * an S3 client or touches AWS credentials. Uses the default credential
 * provider chain, which resolves to the App Runner instance role at deploy
 * with no static keys.
 */
@Configuration
@ConditionalOnProperty(name = "ensemble.photos.backend", havingValue = "s3")
public class StorageConfig {

	@Bean
	S3Client s3Client(DynamoDbProperties dynamoDbProps) {
		return S3Client.builder()
			.region(Region.of(dynamoDbProps.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}
}
