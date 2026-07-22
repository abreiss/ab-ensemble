package com.ensemble.outfit.dto;

import java.time.Instant;

import com.ensemble.outfit.SavedOutfit;

/**
 * Translates between the {@link SavedOutfit} domain/persistence model and the API
 * DTOs, so controllers never touch {@code SavedOutfit} directly and storage
 * internals never leak past the boundary.
 */
public final class OutfitMapper {

	private OutfitMapper() {
	}

	/** Builds the outbound view of a saved outfit. */
	public static OutfitResponse toResponse(SavedOutfit outfit) {
		return new OutfitResponse(
			outfit.getOutfitId(),
			outfit.getItemIds(),
			outfit.getSource(),
			outfit.getReason(),
			outfit.getCreatedAt());
	}

	/**
	 * Builds a persistence entity from a save request plus the server-owned
	 * {@code outfitId} and {@code createdAt} (never client-supplied). The grounding
	 * guard in {@code OutfitService} runs before this, so a returned entity is
	 * already validated.
	 */
	public static SavedOutfit toEntity(SaveOutfitRequest request, String outfitId, Instant createdAt) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(outfitId);
		outfit.setItemIds(request.itemIds());
		outfit.setSource(request.source());
		outfit.setReason(request.reason());
		outfit.setCreatedAt(createdAt);
		return outfit;
	}
}
