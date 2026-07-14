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
 * Creates the wardrobe DynamoDB table on startup if it does not already exist,
 * so a fresh DynamoDB Local is usable with no manual step. Only the partition
 * key ({@code itemId}) is declared — DynamoDB is schemaless for non-key
 * attributes, so the tag/wear-history fields need no table-level definition.
 *
 * <p>Gated by {@code ensemble.dynamodb.auto-create-table} (default true). Tests
 * set it to {@code false} so a Spring context can load without a live DynamoDB;
 * integration tests drive {@link #ensureTable()} directly against TestContainers.
 */
@Component
@ConditionalOnProperty(name = "ensemble.dynamodb.auto-create-table", havingValue = "true", matchIfMissing = true)
public class DynamoDbTableInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

	static final String PARTITION_KEY = "itemId";

	private final DynamoDbClient client;
	private final String tableName;

	public DynamoDbTableInitializer(DynamoDbClient client, DynamoDbProperties props) {
		this.client = client;
		this.tableName = props.tableName();
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureTable();
	}

	/**
	 * Creates the table if it is absent. Idempotent: a no-op when the table
	 * already exists, so it is safe to run on every startup.
	 */
	public void ensureTable() {
		if (tableExists()) {
			log.info("DynamoDB table '{}' already exists", tableName);
			return;
		}
		log.info("Creating DynamoDB table '{}'", tableName);
		client.createTable(b -> b
			.tableName(tableName)
			.keySchema(KeySchemaElement.builder().attributeName(PARTITION_KEY).keyType(KeyType.HASH).build())
			.attributeDefinitions(AttributeDefinition.builder()
				.attributeName(PARTITION_KEY).attributeType(ScalarAttributeType.S).build())
			.billingMode(BillingMode.PAY_PER_REQUEST));
		client.waiter().waitUntilTableExists(w -> w.tableName(tableName));
		log.info("DynamoDB table '{}' created", tableName);
	}

	private boolean tableExists() {
		try {
			client.describeTable(b -> b.tableName(tableName));
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}
}
