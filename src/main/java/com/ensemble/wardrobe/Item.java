package com.ensemble.wardrobe;

import java.time.Instant;
import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A single garment in the wardrobe — the DynamoDB single-table item.
 *
 * <p>Mapped by the AWS SDK v2 Enhanced Client: {@code itemId} is the partition
 * key; the remaining attributes are vision tags plus wear-history. DynamoDB is
 * schemaless for non-key attributes, so no table-level definition is needed for
 * them. A no-arg constructor with getters/setters is required by the bean mapper.
 *
 * <p>The wear-history fields ({@code lastWorn}, {@code wornCount}) are persisted
 * here and mutated by the "I wore this" action (issue #7) via
 * {@code WardrobeService.markWorn} — a deterministic, server-computed increment.
 */
@DynamoDbBean
public class Item {

	private String itemId;
	private String category;
	private String primaryColor;
	private String secondaryColor;
	private Integer formality;
	private String pattern;
	private Integer warmth;
	private List<String> descriptors;
	private String photoKey;
	private Instant createdAt;
	private Instant lastWorn;
	private Integer wornCount;

	@DynamoDbPartitionKey
	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getPrimaryColor() {
		return primaryColor;
	}

	public void setPrimaryColor(String primaryColor) {
		this.primaryColor = primaryColor;
	}

	public String getSecondaryColor() {
		return secondaryColor;
	}

	public void setSecondaryColor(String secondaryColor) {
		this.secondaryColor = secondaryColor;
	}

	public Integer getFormality() {
		return formality;
	}

	public void setFormality(Integer formality) {
		this.formality = formality;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public Integer getWarmth() {
		return warmth;
	}

	public void setWarmth(Integer warmth) {
		this.warmth = warmth;
	}

	public List<String> getDescriptors() {
		return descriptors;
	}

	public void setDescriptors(List<String> descriptors) {
		this.descriptors = descriptors;
	}

	public String getPhotoKey() {
		return photoKey;
	}

	public void setPhotoKey(String photoKey) {
		this.photoKey = photoKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getLastWorn() {
		return lastWorn;
	}

	public void setLastWorn(Instant lastWorn) {
		this.lastWorn = lastWorn;
	}

	public Integer getWornCount() {
		return wornCount;
	}

	public void setWornCount(Integer wornCount) {
		this.wornCount = wornCount;
	}
}
