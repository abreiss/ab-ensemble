package com.ensemble.outfit.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

/**
 * Inbound request to save an outfit. Bean-validation here is defense-in-depth at
 * the controller boundary — {@code itemIds} must be non-empty and {@code source}
 * must be {@code ai} or {@code manual} — but the authoritative check is the
 * save-time grounding guard in {@code OutfitService}, which also verifies every
 * {@code itemId} exists in the wardrobe (the DTO cannot know that). {@code reason}
 * is nullable: populated for AI looks (the stylist's whole-look text), absent for
 * manual looks.
 */
public record SaveOutfitRequest(
	@NotEmpty List<String> itemIds,
	@NotBlank @Pattern(regexp = "ai|manual") String source,
	String reason) {
}
