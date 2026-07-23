package com.ensemble.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
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
 */
@Component
@ConditionalOnProperty(name = "ensemble.dynamodb.auto-create-table", havingValue = "true", matchIfMissing = true)
public class DynamoDbTableInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

	static final String ITEMS_PARTITION_KEY = "itemId";
	static final String OUTFITS_PARTITION_KEY = "outfitId";
	static final String USERS_PARTITION_KEY = "email";

	private final DynamoDbClient client;
	private final DynamoDbProperties props;

	public DynamoDbTableInitializer(DynamoDbClient client, DynamoDbProperties props) {
		this.client = client;
		this.props = props;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureTable(props.tableName(), ITEMS_PARTITION_KEY);
		ensureTable(props.outfitsTableName(), OUTFITS_PARTITION_KEY);
		ensureTable(props.usersTableName(), USERS_PARTITION_KEY);
	}

	/**
	 * Creates the named table (keyed on {@code partitionKey}) if it is absent.
	 * Idempotent: a no-op when the table already exists, so it is safe to run on
	 * every startup.
	 */
	public void ensureTable(String tableName, String partitionKey) {
		if (tableExists(tableName)) {
			log.info("DynamoDB table '{}' already exists", tableName);
			return;
		}
		log.info("Creating DynamoDB table '{}'", tableName);
		client.createTable(b -> b
			.tableName(tableName)
			.keySchema(KeySchemaElement.builder().attributeName(partitionKey).keyType(KeyType.HASH).build())
			.attributeDefinitions(AttributeDefinition.builder()
				.attributeName(partitionKey).attributeType(ScalarAttributeType.S).build())
			.billingMode(BillingMode.PAY_PER_REQUEST));
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
}
