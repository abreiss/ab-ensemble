package com.ensemble.outfit;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

/**
 * Persists {@link SavedOutfit}s via the DynamoDB Enhanced Client against the
 * dedicated outfits table. Thin by design: no relationships, no cascades — just
 * create/read/list/delete keyed on {@code outfitId}. {@link #save} doubles as
 * create and replace (full-item put).
 *
 * <p>Unlike {@link com.ensemble.wardrobe.WardrobeRepository#findUnowned()},
 * {@link #findUnowned()} needs no reserved-prefix filtering: the outfits table is
 * dedicated, so it holds no daily-cap counter rows to exclude.
 */
@Repository
public class OutfitRepository {

	/** Name of the sparse per-user GSI declared on {@link SavedOutfit#getUserId()} (spec #15). */
	private static final String USER_ID_INDEX = "userId-index";

	private final DynamoDbTable<SavedOutfit> table;

	public OutfitRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbProperties props) {
		this.table = enhancedClient.table(props.outfitsTableName(), TableSchema.fromBean(SavedOutfit.class));
	}

	/** Creates or replaces an outfit (full put). Returns the saved outfit. */
	public SavedOutfit save(SavedOutfit outfit) {
		table.putItem(outfit);
		return outfit;
	}

	/** Returns the outfit, or empty if no outfit has that id. */
	public Optional<SavedOutfit> findById(String outfitId) {
		return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(outfitId))));
	}

	/** Returns every saved outfit (demo scale — a full scan). */
	public List<SavedOutfit> findAll() {
		return table.scan().items().stream().toList();
	}

	/**
	 * Returns only the saved outfits owned by {@code userId} via the sparse
	 * {@code userId-index} GSI query — not a full-table scan (spec #15).
	 */
	public List<SavedOutfit> findByUserId(String userId) {
		return table.index(USER_ID_INDEX)
			.query(QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))
			.stream()
			.flatMap(page -> page.items().stream())
			.toList();
	}

	/**
	 * Returns every "unowned" outfit — legacy rows written before per-user ownership
	 * (spec #15) that carry no {@code userId} — for the one-time purge
	 * ({@link com.ensemble.migration.UnownedDataPurgeRunner}). A full-table scan, used
	 * only by the purge, never on a request path. The dedicated outfits table holds no
	 * reserved counter rows, so (unlike the wardrobe scan) no prefix filtering is needed.
	 */
	public List<SavedOutfit> findUnowned() {
		return table.scan().items().stream()
			.filter(outfit -> outfit.getUserId() == null || outfit.getUserId().isBlank())
			.toList();
	}

	/** Removes the outfit with the given id; a no-op if it does not exist. */
	public void deleteById(String outfitId) {
		table.deleteItem(r -> r.key(k -> k.partitionValue(outfitId)));
	}
}
