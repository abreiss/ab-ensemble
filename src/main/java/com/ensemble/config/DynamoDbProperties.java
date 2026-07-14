package com.ensemble.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DynamoDB connection settings. Bound from {@code ensemble.dynamodb.*}.
 *
 * @param endpoint        DynamoDB endpoint (DynamoDB Local in dev, real in deploy)
 * @param region          AWS region name
 * @param tableName       wardrobe table name
 * @param autoCreateTable whether to create the table on startup if absent
 */
@ConfigurationProperties(prefix = "ensemble.dynamodb")
public record DynamoDbProperties(String endpoint, String region, String tableName, boolean autoCreateTable) {
}
