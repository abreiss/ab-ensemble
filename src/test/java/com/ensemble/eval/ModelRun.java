package com.ensemble.eval;

import com.ensemble.tagging.dto.TagSuggestion;

/**
 * One model's outcome for one image: the parsed tags plus the cost/latency signals captured
 * from the live call. {@code parseError} is true when the model replied but its tool JSON
 * could not be parsed into the tag shape (distinct from a legitimately empty/partial tag set).
 * Eval harness only.
 */
public record ModelRun(
	String model,
	String rawJson,
	TagSuggestion tags,
	long latencyMs,
	long inputTokens,
	long outputTokens,
	boolean parseError) {
}
