package com.ensemble.wardrobe.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound vision-tag fields for creating or updating an item. Range constraints
 * enforce the tag schema — {@code formality} 1–5 and {@code warmth} 1–3 — so
 * out-of-range values are rejected with 400 before reaching the domain.
 * Vision tagging (issue #4) will populate these; here they are supplied by the
 * caller.
 */
public record TagRequest(
	@NotBlank String category,
	String primaryColor,
	String secondaryColor,
	@NotNull @Min(1) @Max(5) Integer formality,
	String pattern,
	@NotNull @Min(1) @Max(3) Integer warmth,
	List<String> descriptors) {
}
