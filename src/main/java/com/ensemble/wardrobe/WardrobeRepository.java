package com.ensemble.wardrobe;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

/**
 * Persists {@link Item}s via the DynamoDB Enhanced Client against the single
 * wardrobe table. Thin by design: no relationships, no cascades — just
 * create/read/list/delete keyed on {@code itemId}. {@link #save} doubles as
 * create and update (full-item put), so callers read-modify-write.
 */
@Repository
public class WardrobeRepository {

	/** Name of the sparse per-user GSI declared on {@link Item#getUserId()} (spec #15). */
	private static final String USER_ID_INDEX = "userId-index";

	/** Partition-key prefix of the reserved {@code usage#<date>} daily-cap counter rows. */
	private static final String USAGE_ROW_PREFIX = "usage#";

	private final DynamoDbTable<Item> table;

	public WardrobeRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbProperties props) {
		this.table = enhancedClient.table(props.tableName(), TableSchema.fromBean(Item.class));
	}

	/**
	 * Creates or replaces an item (full put). Returns the saved item.
	 *
	 * <p><strong>Owner-stamp precondition (spec #15).</strong> {@code userId} is the
	 * entire per-user security boundary, so this persistence chokepoint refuses to
	 * write a row with a null or blank owner — the last-line guarantee that no
	 * owner-less ("unowned") row is ever persisted, independent of whether an
	 * individual caller remembered to stamp the owner. A legacy pre-#15 unowned row
	 * or a reserved {@code usage#<date>} counter row therefore never reaches the
	 * table through this method; they exist only via paths that predate or bypass
	 * this guard (and the purge exists to remove the former).
	 */
	public Item save(Item item) {
		if (item.getUserId() == null || item.getUserId().isBlank()) {
			throw new IllegalStateException(
				"refusing to persist an item with no owner (userId); every write must be owner-stamped (spec #15)");
		}
		table.putItem(item);
		return item;
	}

	/** Returns the item, or empty if no item has that id. */
	public Optional<Item> findById(String itemId) {
		return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(itemId))));
	}

	/**
	 * Returns only the items owned by {@code userId} via the sparse
	 * {@code userId-index} GSI query — not a full-table scan (spec #15). Reserved
	 * {@code usage#<date>} counter rows carry no {@code userId}, so they are absent
	 * from the index and never surface here.
	 */
	public List<Item> findByUserId(String userId) {
		return table.index(USER_ID_INDEX)
			.query(QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))
			.stream()
			.flatMap(page -> page.items().stream())
			.toList();
	}

	/**
	 * Returns every "unowned" item — legacy rows written before per-user ownership
	 * (spec #15) that carry no {@code userId} — for the one-time purge
	 * ({@link com.ensemble.migration.UnownedDataPurgeRunner}). A full-table scan: the
	 * sparse {@code userId-index} deliberately cannot surface null-{@code userId} rows,
	 * so it is unusable here, and this is never called on a request path. Reserved
	 * {@code usage#<date>} daily-cap counter rows also carry no {@code userId} but are
	 * legitimate, so they are excluded by partition-key prefix.
	 */
	public List<Item> findUnowned() {
		return table.scan().items().stream()
			.filter(item -> item.getUserId() == null || item.getUserId().isBlank())
			.filter(item -> !item.getItemId().startsWith(USAGE_ROW_PREFIX))
			.toList();
	}

	/** Removes the item with the given id; a no-op if it does not exist. */
	public void deleteById(String itemId) {
		table.deleteItem(r -> r.key(k -> k.partitionValue(itemId)));
	}
}
