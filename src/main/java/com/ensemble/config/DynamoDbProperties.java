package com.ensemble.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DynamoDB connection settings. Bound from {@code ensemble.dynamodb.*}.
 *
 * @param endpoint          DynamoDB endpoint (DynamoDB Local in dev, real in deploy)
 * @param region            AWS region name
 * @param tableName         wardrobe (items) table name
 * @param outfitsTableName  saved-outfits table name (dedicated table, issue #26)
 * @param usersTableName    user-accounts table name (dedicated table, issue #14)
 * @param autoCreateTable   whether to create the tables on startup if absent
 */
@ConfigurationProperties(prefix = "ensemble.dynamodb")
public record DynamoDbProperties(
	String endpoint,
	String region,
	String tableName,
	String outfitsTableName,
	String usersTableName,
	boolean autoCreateTable) {
}
