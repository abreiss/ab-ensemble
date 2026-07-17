package com.ensemble.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ensemble.config.DynamoDbProperties;
import com.ensemble.config.DynamoDbTableInitializer;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Real DynamoDB Local round-trips for {@link UsageRepository} via TestContainers,
 * mirroring {@code WardrobeRepositoryIT}. Each test runs against a fresh,
 * uniquely-named table so cases are fully isolated — no Spring context.
 */
@Testcontainers
class UsageRepositoryIT {

	private static final int PORT = 8000;

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private DynamoDbClient client;
	private String tableName;
	private UsageRepository repository;

	@BeforeEach
	void setUp() {
		String endpoint = "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(PORT);
		client = DynamoDbClient.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create("local", "local")))
			.build();

		tableName = "items-" + UUID.randomUUID();
		DynamoDbProperties props = new DynamoDbProperties(endpoint, "us-east-1", tableName, true);
		new DynamoDbTableInitializer(client, props).ensureTable();
		repository = new UsageRepository(client, props);
	}

	@Test
	void incrementIsAtomicAndPersists() {
		long first = repository.increment("2026-07-16");
		long second = repository.increment("2026-07-16");

		assertThat(first).isEqualTo(1L);
		assertThat(second).isEqualTo(2L);

		GetItemResponse stored = client.getItem(b -> b
			.tableName(tableName)
			.key(Map.of("itemId", AttributeValue.builder().s("usage#2026-07-16").build())));
		assertThat(stored.item().get("count").n()).isEqualTo("2");
	}

	@Test
	void incrementForDifferentDates_tracksSeparateCounters() {
		repository.increment("2026-07-16");
		repository.increment("2026-07-16");
		long otherDay = repository.increment("2026-07-17");

		assertThat(otherDay).isEqualTo(1L);
	}
}
