package com.ensemble.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Creates the DynamoDB tables on startup if they do not already exist, so a fresh
 * DynamoDB Local is usable with no manual step. Ensures the wardrobe (items)
 * table, the dedicated saved-outfits table (issue #26), and the dedicated
 * user-accounts table (issue #14). Only each table's partition key is declared —
 * DynamoDB is schemaless for non-key attributes, so the remaining fields need no
 * table-level definition.
 *
 * <p>Gated by {@code ensemble.dynamodb.auto-create-table} (default true). Tests
 * set it to {@code false} so a Spring context can load without a live DynamoDB;
 * integration tests drive {@link #ensureTable(String, String)} directly against
 * TestContainers.
 *
 * <p>Ordered to run before {@code SeedAccountRunner} (issue #14), which seeds a
 * default account into the users table this runner creates in local dev.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@ConditionalOnProperty(name = "ensemble.dynamodb.auto-create-table", havingValue = "true", matchIfMissing = true)
public class DynamoDbTableInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

	static final String ITEMS_PARTITION_KEY = "itemId";
	static final String OUTFITS_PARTITION_KEY = "outfitId";
	static final String USERS_PARTITION_KEY = "email";

	/** Sparse per-user GSI (spec #15): items/outfits carry {@code userId}; the users table does not. */
	static final String USER_ID_ATTRIBUTE = "userId";
	static final String USER_ID_INDEX = "userId-index";

	private final DynamoDbClient client;
	private final DynamoDbProperties props;

	public DynamoDbTableInitializer(DynamoDbClient client, DynamoDbProperties props) {
		this.client = client;
		this.props = props;
	}

	@Override
	public void run(ApplicationArguments args) {
		// items + outfits are per-user queryable (spec #15) -> declare the userId GSI;
		// the users table is looked up by email only and stays plain.
		ensureTable(props.tableName(), ITEMS_PARTITION_KEY, USER_ID_ATTRIBUTE, USER_ID_INDEX);
		ensureTable(props.outfitsTableName(), OUTFITS_PARTITION_KEY, USER_ID_ATTRIBUTE, USER_ID_INDEX);
		ensureTable(props.usersTableName(), USERS_PARTITION_KEY);
	}

	/**
	 * Creates the named table (keyed on {@code partitionKey}) with no secondary
	 * index if it is absent. Idempotent: a no-op when the table already exists.
	 */
	public void ensureTable(String tableName, String partitionKey) {
		ensureTable(tableName, partitionKey, null, null);
	}

	/**
	 * Creates the named table if it is absent, additionally declaring a sparse
	 * global secondary index (HASH {@code gsiPartitionKey}, projection ALL) when
	 * both {@code gsiPartitionKey} and {@code gsiIndexName} are non-null. Under
	 * {@code PAY_PER_REQUEST} the GSI needs no throughput.
	 *
	 * <p>Idempotent: when the table already exists this is a no-op, except it logs
	 * a WARN if a requested GSI is missing — a pre-#15 local table is skipped here
	 * (initializers never mutate existing tables), so per-user queries would fail
	 * at runtime; the WARN turns that silent footgun into an actionable message
	 * (drop the table so it is recreated with the index). Deployed tables get the
	 * index via Terraform's in-place {@code UpdateTable}.
	 */
	public void ensureTable(String tableName, String partitionKey, String gsiPartitionKey, String gsiIndexName) {
		boolean withGsi = gsiPartitionKey != null && gsiIndexName != null;
		if (tableExists(tableName)) {
			log.info("DynamoDB table '{}' already exists", tableName);
			if (withGsi) {
				warnIfMissingIndex(tableName, gsiIndexName);
			}
			return;
		}
		log.info("Creating DynamoDB table '{}'", tableName);
		client.createTable(b -> {
			b.tableName(tableName)
				.keySchema(KeySchemaElement.builder().attributeName(partitionKey).keyType(KeyType.HASH).build())
				.billingMode(BillingMode.PAY_PER_REQUEST);
			if (withGsi) {
				b.attributeDefinitions(
						AttributeDefinition.builder().attributeName(partitionKey).attributeType(ScalarAttributeType.S).build(),
						AttributeDefinition.builder().attributeName(gsiPartitionKey).attributeType(ScalarAttributeType.S).build())
					.globalSecondaryIndexes(GlobalSecondaryIndex.builder()
						.indexName(gsiIndexName)
						.keySchema(KeySchemaElement.builder().attributeName(gsiPartitionKey).keyType(KeyType.HASH).build())
						.projection(Projection.builder().projectionType(ProjectionType.ALL).build())
						.build());
			} else {
				b.attributeDefinitions(AttributeDefinition.builder()
					.attributeName(partitionKey).attributeType(ScalarAttributeType.S).build());
			}
		});
		client.waiter().waitUntilTableExists(w -> w.tableName(tableName));
		log.info("DynamoDB table '{}' created", tableName);
	}

	private boolean tableExists(String tableName) {
		try {
			client.describeTable(b -> b.tableName(tableName));
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	private void warnIfMissingIndex(String tableName, String indexName) {
		var table = client.describeTable(b -> b.tableName(tableName)).table();
		boolean hasIndex = table.hasGlobalSecondaryIndexes()
			&& table.globalSecondaryIndexes().stream().anyMatch(gsi -> indexName.equals(gsi.indexName()));
		if (!hasIndex) {
			log.warn("DynamoDB table '{}' predates the '{}' index; per-user queries will fail. "
				+ "Drop the table so it is recreated with the index (local dev), or apply the "
				+ "Terraform GSI (deploy).", tableName, indexName);
		}
	}
}
