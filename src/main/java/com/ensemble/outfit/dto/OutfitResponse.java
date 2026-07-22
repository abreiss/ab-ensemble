package com.ensemble.outfit.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbound saved-outfit representation. Carries the ordered member ids, how the
 * look was assembled ({@code source}), the whole-look rationale when present
 * ({@code reason}, null for manual looks), and the server-owned {@code createdAt}.
 * The client resolves each {@code itemId} to a photo via the existing
 * {@code photoUrl} builder, so no photo bytes are embedded here. No DynamoDB
 * internals leak past this boundary.
 */
public record OutfitResponse(
	String outfitId,
	List<String> itemIds,
	String source,
	String reason,
	Instant createdAt) {
}
