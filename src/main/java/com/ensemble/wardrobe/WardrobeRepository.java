package com.ensemble.wardrobe;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Persists {@link Item}s via the DynamoDB Enhanced Client against the single
 * wardrobe table. Thin by design: no relationships, no cascades — just
 * create/read/list/delete keyed on {@code itemId}. {@link #save} doubles as
 * create and update (full-item put), so callers read-modify-write.
 */
@Repository
public class WardrobeRepository {

	private final DynamoDbTable<Item> table;

	public WardrobeRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbProperties props) {
		this.table = enhancedClient.table(props.tableName(), TableSchema.fromBean(Item.class));
	}

	/** Creates or replaces an item (full put). Returns the saved item. */
	public Item save(Item item) {
		table.putItem(item);
		return item;
	}

	/** Returns the item, or empty if no item has that id. */
	public Optional<Item> findById(String itemId) {
		return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(itemId))));
	}

	/** Returns every item in the wardrobe (demo scale — a full scan). */
	public List<Item> findAll() {
		return table.scan().items().stream().toList();
	}

	/** Removes the item with the given id; a no-op if it does not exist. */
	public void deleteById(String itemId) {
		table.deleteItem(r -> r.key(k -> k.partitionValue(itemId)));
	}
}
