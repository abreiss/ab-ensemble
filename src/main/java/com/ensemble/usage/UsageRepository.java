package com.ensemble.usage;

import java.util.Map;

import org.springframework.stereotype.Repository;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/**
 * Atomic daily call-counter, sharing the wardrobe's single DynamoDB table under a
 * reserved {@code usage#<UTC-date>} partition key so no separate table is needed.
 * Uses the low-level {@link DynamoDbClient} directly (not the enhanced/bean client)
 * because the enhanced client's item-shaped mapper cannot express an atomic
 * {@code ADD} update.
 */
@Repository
public class UsageRepository {

	private static final String COUNT_ATTRIBUTE = "count";

	private final DynamoDbClient client;
	private final String tableName;

	public UsageRepository(DynamoDbClient client, DynamoDbProperties props) {
		this.client = client;
		this.tableName = props.tableName();
	}

	/**
	 * Atomically increments the counter for the given UTC date and returns the new
	 * count. A never-before-seen date starts implicitly at 0 (DynamoDB's {@code ADD}
	 * creates the item/attribute if absent), so the counter "resets" simply by using
	 * a new day's key.
	 */
	public long increment(String utcDate) {
		UpdateItemResponse response = client.updateItem(b -> b
			.tableName(tableName)
			.key(Map.of("itemId", AttributeValue.builder().s("usage#" + utcDate).build()))
			.updateExpression("ADD #c :one")
			.expressionAttributeNames(Map.of("#c", COUNT_ATTRIBUTE))
			.expressionAttributeValues(Map.of(":one", AttributeValue.builder().n("1").build()))
			.returnValues(ReturnValue.UPDATED_NEW));
		return Long.parseLong(response.attributes().get(COUNT_ATTRIBUTE).n());
	}
}
