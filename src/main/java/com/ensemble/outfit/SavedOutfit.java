package com.ensemble.outfit;

import java.time.Instant;
import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A saved outfit — a look the user chose to keep, persisted as a first-class
 * DynamoDB item in its own dedicated outfits table.
 *
 * <p>Mapped by the AWS SDK v2 Enhanced Client: {@code outfitId} is the partition
 * key; the remaining attributes carry the ordered member ids, how the look was
 * assembled, and (for AI looks) the stylist's whole-look rationale. DynamoDB is
 * schemaless for non-key attributes, so no table-level definition is needed for
 * them. A no-arg constructor with getters/setters is required by the bean mapper.
 *
 * <p>Named {@code SavedOutfit} — not {@code Outfit} — because
 * {@code com.ensemble.stylist.Outfit} already exists as an ephemeral stylist-pick
 * value record; this is the persisted entity.
 */
@DynamoDbBean
public class SavedOutfit {

	private String outfitId;
	private List<String> itemIds;
	private String source;
	private String reason;
	private Instant createdAt;

	@DynamoDbPartitionKey
	public String getOutfitId() {
		return outfitId;
	}

	public void setOutfitId(String outfitId) {
		this.outfitId = outfitId;
	}

	public List<String> getItemIds() {
		return itemIds;
	}

	public void setItemIds(List<String> itemIds) {
		this.itemIds = itemIds;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
