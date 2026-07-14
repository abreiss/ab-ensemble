package com.ensemble.wardrobe.dto;

import com.ensemble.wardrobe.Item;

/**
 * Translates between the {@link Item} domain/persistence model and the API DTOs,
 * so controllers never touch {@code Item} directly and storage internals never
 * leak past the boundary.
 */
public final class ItemMapper {

	static final String PHOTO_PATH_TEMPLATE = "/api/items/%s/photo";

	private ItemMapper() {
	}

	/** Builds the outbound view of an item, including its photo URL. */
	public static ItemResponse toResponse(Item item) {
		return new ItemResponse(
			item.getItemId(),
			item.getCategory(),
			item.getPrimaryColor(),
			item.getSecondaryColor(),
			item.getFormality(),
			item.getPattern(),
			item.getWarmth(),
			item.getDescriptors(),
			PHOTO_PATH_TEMPLATE.formatted(item.getItemId()),
			item.getCreatedAt(),
			item.getLastWorn(),
			item.getWornCount());
	}

	/** Copies the tag fields from a request onto an item (create or update). */
	public static void applyTags(Item item, TagRequest tags) {
		item.setCategory(tags.category());
		item.setPrimaryColor(tags.primaryColor());
		item.setSecondaryColor(tags.secondaryColor());
		item.setFormality(tags.formality());
		item.setPattern(tags.pattern());
		item.setWarmth(tags.warmth());
		item.setDescriptors(tags.descriptors());
	}
}
