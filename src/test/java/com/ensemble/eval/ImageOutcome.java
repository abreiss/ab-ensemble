package com.ensemble.eval;

import com.ensemble.eval.AccuracyScorer.TagScore;

/**
 * Everything the report needs for one image: the gold label, both models' runs and their
 * accuracy scores, and (when {@code judged}) the de-anonymized judge result plus its cost.
 * When not judged, the judge fields are empty/zero. Eval harness only.
 */
public record ImageOutcome(
	String image,
	GoldTag gold,
	ModelRun haiku,
	ModelRun sonnet,
	TagScore haikuScore,
	TagScore sonnetScore,
	boolean judged,
	String judgeModelA,
	String judgeModelB,
	String judgeWinner,
	String judgeReason,
	long judgeInputTokens,
	long judgeOutputTokens,
	long judgeLatencyMs) {
}
