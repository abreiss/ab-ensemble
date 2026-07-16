package com.ensemble.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One ground-truth ("gold") label from {@code eval/tagging/gold.json}: the human-authored
 * expected tags for a garment photo, keyed by {@code image} (the filename under the images
 * dir). Field shape mirrors {@link com.ensemble.tagging.dto.TagSuggestion} so a model's
 * prediction can be scored field-by-field against it.
 *
 * <p>Part of the offline model-eval harness only — never compiled into the app jar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldTag(
	String image,
	String category,
	String primaryColor,
	String secondaryColor,
	Integer formality,
	String pattern,
	Integer warmth,
	List<String> descriptors) {
}
