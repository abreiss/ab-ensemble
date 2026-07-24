package com.ensemble.config;

import java.util.Objects;

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
		GsiSpec userIdGsi = new GsiSpec(USER_ID_ATTRIBUTE, USER_ID_INDEX);
		ensureTable(props.tableName(), ITEMS_PARTITION_KEY, userIdGsi);
		ensureTable(props.outfitsTableName(), OUTFITS_PARTITION_KEY, userIdGsi);
		ensureTable(props.usersTableName(), USERS_PARTITION_KEY);
	}

	/**
	 * A complete secondary-index declaration: the GSI's partition-key attribute
	 * <em>and</em> its index name, carried together as one value. Modeling the pair
	 * as a single record — rather than two independently-nullable {@code String}
	 * parameters — makes "has a GSI" and "both required fields present" a single
	 * type-checked thing (spec #15 PR review, finding 3): the partial-null state
	 * (one set, the other null), which previously fell through to a silently
	 * index-less table, is unrepresentable. The compact constructor rejects a
	 * null/blank field so the pair is always whole.
	 */
	public record GsiSpec(String partitionKeyAttribute, String indexName) {
		public GsiSpec {
			if (partitionKeyAttribute == null || partitionKeyAttribute.isBlank()) {
				throw new IllegalArgumentException("GSI partitionKeyAttribute must not be null or blank");
			}
			if (indexName == null || indexName.isBlank()) {
				throw new IllegalArgumentException("GSI indexName must not be null or blank");
			}
		}
	}

	/**
	 * Creates the named table (keyed on {@code partitionKey}) with no secondary
	 * index if it is absent. Idempotent: a no-op when the table already exists.
	 */
	public void ensureTable(String tableName, String partitionKey) {
		createIfAbsent(tableName, partitionKey, null);
	}

	/**
	 * Creates the named table if it is absent, additionally declaring the sparse
	 * global secondary index described by {@code gsi} (HASH on its
	 * {@code partitionKeyAttribute}, projection ALL). Under {@code PAY_PER_REQUEST}
	 * the GSI needs no throughput. {@code gsi} is required — use the two-arg
	 * {@link #ensureTable(String, String)} for the no-index case.
	 *
	 * <p>Idempotent: when the table already exists this is a no-op, except it logs
	 * a WARN if the requested GSI is missing — a pre-#15 local table is skipped here
	 * (initializers never mutate existing tables), so per-user queries would fail
	 * at runtime; the WARN turns that silent footgun into an actionable message
	 * (drop the table so it is recreated with the index). Deployed tables get the
	 * index via Terraform's in-place {@code UpdateTable}.
	 */
	public void ensureTable(String tableName, String partitionKey, GsiSpec gsi) {
		Objects.requireNonNull(gsi, "gsi must not be null; use ensureTable(tableName, partitionKey) for the no-index case");
		createIfAbsent(tableName, partitionKey, gsi);
	}

	/** Shared table-creation body; {@code gsi} null means create with no secondary index. */
	private void createIfAbsent(String tableName, String partitionKey, GsiSpec gsi) {
		boolean withGsi = gsi != null;
		if (tableExists(tableName)) {
			log.info("DynamoDB table '{}' already exists", tableName);
			if (withGsi) {
				warnIfMissingIndex(tableName, gsi.indexName());
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
						AttributeDefinition.builder().attributeName(gsi.partitionKeyAttribute()).attributeType(ScalarAttributeType.S).build())
					.globalSecondaryIndexes(GlobalSecondaryIndex.builder()
						.indexName(gsi.indexName())
						.keySchema(KeySchemaElement.builder().attributeName(gsi.partitionKeyAttribute()).keyType(KeyType.HASH).build())
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
