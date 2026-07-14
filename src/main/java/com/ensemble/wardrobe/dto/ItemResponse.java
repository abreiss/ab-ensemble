package com.ensemble.wardrobe.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbound item representation. Carries the tags and wear-history plus a
 * {@code photoUrl} the client fetches separately — the photo bytes are never
 * embedded, so the wardrobe grid can lazy-load thumbnails. No DynamoDB or
 * storage internals leak here.
 */
public record ItemResponse(
	String itemId,
	String category,
	String primaryColor,
	String secondaryColor,
	Integer formality,
	String pattern,
	Integer warmth,
	List<String> descriptors,
	String photoUrl,
	Instant createdAt,
	Instant lastWorn,
	Integer wornCount) {
}
