package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Verifies the startup table bootstrap against a real DynamoDB Local
 * (TestContainers). Drives {@link DynamoDbTableInitializer} directly — no Spring
 * context — so the check is fast and isolated. The initializer ensures the
 * items, the (dedicated) outfits table (issue #26), and the (dedicated) users
 * table (issue #14) on startup, so these tests assert all are created and that a
 * re-run is idempotent.
 */
@Testcontainers
class DynamoDbTableInitializerIT {

	private static final int PORT = 8000;
	private static final String ITEMS_TABLE = "ensemble-items";
	private static final String OUTFITS_TABLE = "ensemble-outfits";
	private static final String USERS_TABLE = "ensemble-users";

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
		return new DynamoDbProperties("unused", "us-east-1", ITEMS_TABLE, OUTFITS_TABLE, USERS_TABLE, true);
	}

	@Test
	void run_whenAbsent_createsItemsOutfitsAndUsersTables() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.run(new DefaultApplicationArguments());

		assertThat(client.listTables().tableNames()).contains(ITEMS_TABLE, OUTFITS_TABLE, USERS_TABLE);
		// The users table is keyed on the normalized username partition key (issue #34).
		assertThat(partitionKey(client, USERS_TABLE)).isEqualTo("username");
	}

	@Test
	void run_whenAlreadyPresent_isIdempotent() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.run(new DefaultApplicationArguments());

		// A second run must not throw (ResourceInUse) and must leave one of each table.
		assertThatCode(() -> initializer.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
		assertThat(client.listTables().tableNames()).containsOnlyOnce(ITEMS_TABLE);
		assertThat(client.listTables().tableNames()).containsOnlyOnce(OUTFITS_TABLE);
		assertThat(client.listTables().tableNames()).containsOnlyOnce(USERS_TABLE);
	}

	@Test
	void ensureTable_createsTableWithGivenPartitionKey() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.ensureTable("adhoc-table", "outfitId");

		assertThat(client.listTables().tableNames()).contains("adhoc-table");
	}

	@Test
	void run_createsItemsAndOutfitsTablesWithUserIdIndex() {
		DynamoDbClient client = client();
		DynamoDbTableInitializer initializer = new DynamoDbTableInitializer(client, props());

		initializer.run(new DefaultApplicationArguments());

		// The per-user GSI (spec #15) is declared on the items and outfits tables...
		assertThat(indexNames(client, ITEMS_TABLE)).contains("userId-index");
		assertThat(indexNames(client, OUTFITS_TABLE)).contains("userId-index");
		// ...but the users table stays plain (username partition key only, no GSI).
		assertThat(indexNames(client, USERS_TABLE)).isEmpty();
	}

	private static String partitionKey(DynamoDbClient client, String tableName) {
		var table = client.describeTable(b -> b.tableName(tableName)).table();
		return table.keySchema().stream()
			.filter(k -> k.keyType() == software.amazon.awssdk.services.dynamodb.model.KeyType.HASH)
			.map(software.amazon.awssdk.services.dynamodb.model.KeySchemaElement::attributeName)
			.findFirst()
			.orElseThrow();
	}

	private static List<String> indexNames(DynamoDbClient client, String tableName) {
		var table = client.describeTable(b -> b.tableName(tableName)).table();
		if (!table.hasGlobalSecondaryIndexes()) {
			return List.of();
		}
		return table.globalSecondaryIndexes().stream()
			.map(software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription::indexName)
			.toList();
	}
}
