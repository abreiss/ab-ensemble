package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

/**
 * Verifies the startup table bootstrap against a real DynamoDB Local
 * (TestContainers). Drives {@link DynamoDbTableInitializer#ensureTable()}
 * directly — no Spring context — so the check is fast and isolated.
 */
@Testcontainers
class DynamoDbTableInitializerIT {

	private static final int PORT = 8000;
	private static final String TABLE = "ensemble-items";

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private DynamoDbClient client() {
		String endpoint = "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(PORT);
		return DynamoDbClient.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create("local", "local")))
			.build();
	}

	private DynamoDbProperties props() {
		return new DynamoDbProperties("unused", "us-east-1", TABLE, true);
	}

	@Test
	void ensureTable_whenAbsent_createsTable() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.ensureTable();

		ListTablesResponse tables = client.listTables();
		assertThat(tables.tableNames()).contains(TABLE);
	}

	@Test
	void ensureTable_whenAlreadyPresent_isIdempotent() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.ensureTable();

		// A second run must not throw (ResourceInUse) and must leave one table.
		assertThatCode(initializer::ensureTable).doesNotThrowAnyException();
		assertThat(client.listTables().tableNames()).containsOnlyOnce(TABLE);
	}
}
