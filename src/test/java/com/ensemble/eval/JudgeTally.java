package com.ensemble.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ensemble.eval.JudgeResultParser.JudgeVerdict;

/**
 * De-anonymizes blind judge verdicts back to model wins. The judge only ever sees "set A" vs
 * "set B" (order randomized per image to kill position bias), so each image records which model
 * was A and which was B; this tallies the {@code winner} back onto the real model names.
 *
 * <p>Getting this mapping wrong silently corrupts the whole comparison, so it is isolated and
 * unit-tested. Eval harness only.
 */
public final class JudgeTally {

	/** One image's blind assignment plus the verdict the judge returned for it. */
	public record ImageJudgement(String modelA, String modelB, JudgeVerdict verdict) {
	}

	/** Win counts keyed by real model name, plus the number of ties. */
	public record Tally(Map<String, Integer> wins, int ties) {
	}

	private JudgeTally() {
	}

	public static Tally tally(List<ImageJudgement> judgements) {
		Map<String, Integer> wins = new LinkedHashMap<>();
		int ties = 0;
		for (ImageJudgement j : judgements) {
			wins.putIfAbsent(j.modelA(), 0);
			wins.putIfAbsent(j.modelB(), 0);
			switch (j.verdict().winner()) {
				case "A" -> wins.merge(j.modelA(), 1, Integer::sum);
				case "B" -> wins.merge(j.modelB(), 1, Integer::sum);
				default -> ties++;
			}
		}
		return new Tally(wins, ties);
	}
}
