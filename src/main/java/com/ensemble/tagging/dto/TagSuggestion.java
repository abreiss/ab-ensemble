package com.ensemble.tagging.dto;

import java.util.List;

/**
 * Outbound auto-tag suggestion: the wardrobe's tag fields as an <strong>all-nullable</strong>
 * view. Deliberately carries <strong>no</strong> bean-validation constraints — unlike
 * {@link com.ensemble.wardrobe.dto.TagRequest}, whose constraints apply only when the user
 * finally saves. A suggestion may legitimately be partial or empty (a degraded or failed
 * vision call still returns an editable {@code 200}), so a null in any field is a normal,
 * renderable state rather than an error.
 *
 * @param category the garment category, or {@code null} if undetermined
 * @param primaryColor the dominant color, or {@code null}
 * @param secondaryColor a secondary color, or {@code null}
 * @param formality formality 1–5, or {@code null} if undetermined/out of range
 * @param pattern the pattern, or {@code null}
 * @param warmth warmth 1–3, or {@code null} if undetermined/out of range
 * @param descriptors free-form descriptor tags, or {@code null} if none were produced
 */
public record TagSuggestion(
	String category,
	String primaryColor,
	String secondaryColor,
	Integer formality,
	String pattern,
	Integer warmth,
	List<String> descriptors) {

	private static final TagSuggestion EMPTY =
		new TagSuggestion(null, null, null, null, null, null, null);

	/** The all-empty suggestion returned on any tagging failure or absence of output. */
	public static TagSuggestion empty() {
		return EMPTY;
	}
}
