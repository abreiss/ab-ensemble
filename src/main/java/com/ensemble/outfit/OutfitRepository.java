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
 * <p>Unlike {@code WardrobeRepository.findAll}, {@link #findAll} needs no
 * reserved-prefix filtering: the outfits table is dedicated, so it holds no
 * daily-cap counter rows to exclude.
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

	/** Removes the outfit with the given id; a no-op if it does not exist. */
	public void deleteById(String outfitId) {
		table.deleteItem(r -> r.key(k -> k.partitionValue(outfitId)));
	}
}
