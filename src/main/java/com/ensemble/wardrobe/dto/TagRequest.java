package com.ensemble.wardrobe.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound vision-tag fields for creating or updating an item. Range constraints
 * enforce the tag schema — {@code formality} 1–5 and {@code warmth} 1–3 — so a
 * <em>supplied</em> out-of-range value is still rejected with 400 before reaching
 * the domain. {@code formality}/{@code warmth} are otherwise optional (a
 * {@code null} is valid — Jakarta's {@code @Min}/{@code @Max} pass on {@code
 * null}) so categories where they do not apply (e.g. Jewelry, Accessory) can
 * save without them. {@code category} remains required; unrecognized/off-taxonomy
 * values are never rejected here — they are normalized to a taxonomy bucket at
 * the save-path choke point ({@code ItemMapper.applyTags}), not validated against
 * an enum. Vision tagging (issue #4) will populate these; here they are supplied
 * by the caller.
 */
public record TagRequest(
	@NotBlank String category,
	String primaryColor,
	String secondaryColor,
	@Min(1) @Max(5) Integer formality,
	String pattern,
	@Min(1) @Max(3) Integer warmth,
	List<String> descriptors) {
}
