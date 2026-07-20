package com.ensemble.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Wires the AWS SDK v2 DynamoDB clients. The low-level {@link DynamoDbClient}
 * switches on whether {@code ensemble.dynamodb.endpoint} is set: present (e.g.
 * DynamoDB Local in dev) overrides the endpoint and uses static dummy
 * credentials; blank/absent (real AWS at deploy) leaves the SDK's built-in
 * endpoint resolution alone and uses the default credential provider chain
 * (the App Runner instance role, with no static keys). The
 * {@link DynamoDbEnhancedClient} layers bean mapping on top. Client
 * construction is lazy — no network call happens until first use — so a
 * Spring context can start without a live DynamoDB.
 */
@Configuration
@EnableConfigurationProperties({DynamoDbProperties.class, PhotoProperties.class})
public class DynamoDbConfig {

	@Bean
	DynamoDbClient dynamoDbClient(DynamoDbProperties props) {
		DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(props.region()));
		if (isLocalEndpoint(props.endpoint())) {
			builder.endpointOverride(URI.create(props.endpoint()))
				.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create("local", "local")));
		} else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}
		return builder.build();
	}

	/** The testable seam for the local↔cloud switch: blank/absent means real AWS. */
	static boolean isLocalEndpoint(String endpoint) {
		return endpoint != null && !endpoint.isBlank();
	}

	@Bean
	DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
		return DynamoDbEnhancedClient.builder()
			.dynamoDbClient(dynamoDbClient)
			.build();
	}
}
