package com.ensemble.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Wires the AWS SDK v2 DynamoDB clients. The low-level {@link DynamoDbClient}
 * points at the configured endpoint (DynamoDB Local in dev) with static dummy
 * credentials; the {@link DynamoDbEnhancedClient} layers bean mapping on top.
 * Client construction is lazy — no network call happens until first use — so a
 * Spring context can start without a live DynamoDB.
 */
@Configuration
@EnableConfigurationProperties({DynamoDbProperties.class, PhotoProperties.class})
public class DynamoDbConfig {

	@Bean
	DynamoDbClient dynamoDbClient(DynamoDbProperties props) {
		return DynamoDbClient.builder()
			.endpointOverride(URI.create(props.endpoint()))
			.region(Region.of(props.region()))
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create("local", "local")))
			.build();
	}

	@Bean
	DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
		return DynamoDbEnhancedClient.builder()
			.dynamoDbClient(dynamoDbClient)
			.build();
	}
}
