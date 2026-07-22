package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsServiceClientConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Verifies the local↔cloud switch on the low-level {@link DynamoDbClient}: a
 * blank/absent {@code ensemble.dynamodb.endpoint} must fall back to the
 * default credential provider chain with no endpoint override (real AWS,
 * e.g. the App Runner instance role); a present endpoint (DynamoDB Local in
 * dev) must override the endpoint and use static dummy credentials, exactly
 * as before. Inspecting {@link DynamoDbClient#serviceClientConfiguration()}
 * asserts the actual built client rather than reflecting into private state.
 */
class DynamoDbConfigTest {

	private final DynamoDbConfig config = new DynamoDbConfig();

	@Test
	void blankEndpoint_usesDefaultCredentialsWithNoOverride() {
		DynamoDbProperties props = new DynamoDbProperties("", "us-east-1", "ensemble-items", "ensemble-outfits", false);

		DynamoDbClient client = config.dynamoDbClient(props);

		AwsServiceClientConfiguration clientConfig = client.serviceClientConfiguration();
		assertThat(clientConfig.endpointOverride()).isEmpty();
		assertThat(clientConfig.credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
	}

	@Test
	void absentEndpoint_usesDefaultCredentialsWithNoOverride() {
		DynamoDbProperties props = new DynamoDbProperties(null, "us-east-1", "ensemble-items", "ensemble-outfits", false);

		DynamoDbClient client = config.dynamoDbClient(props);

		AwsServiceClientConfiguration clientConfig = client.serviceClientConfiguration();
		assertThat(clientConfig.endpointOverride()).isEmpty();
		assertThat(clientConfig.credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
	}

	@Test
	void presentEndpoint_overridesEndpointWithStaticDummyCredentials() {
		DynamoDbProperties props =
			new DynamoDbProperties("http://localhost:8000", "us-east-1", "ensemble-items", "ensemble-outfits", false);

		DynamoDbClient client = config.dynamoDbClient(props);

		AwsServiceClientConfiguration clientConfig = client.serviceClientConfiguration();
		assertThat(clientConfig.endpointOverride()).contains(URI.create("http://localhost:8000"));
		assertThat(clientConfig.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
	}
}
