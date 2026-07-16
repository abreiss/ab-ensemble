package com.ensemble.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Scores a model's predicted {@link TagSuggestion} against a human {@link GoldTag}, field by
 * field, and aggregates those scores across a run.
 *
 * <p>Scoring rules:
 * <ul>
 *   <li><b>String fields</b> (category, colors, pattern): normalized (trim + lowercase) equality.
 *       Two {@code null}s agree (both "undetermined"); a one-sided {@code null} is a mismatch.</li>
 *   <li><b>Scalar fields</b> (formality 1-5, warmth 1-3): exact match, plus a lenient
 *       within-±1 match (a 1-vs-2 formality is "close"). Two {@code null}s agree.</li>
 *   <li><b>Descriptors</b>: set precision / recall / F1 over normalized tokens.</li>
 * </ul>
 *
 * <p>Pure and network-free so every rule and edge case is unit-tested. Eval harness only.
 */
public final class AccuracyScorer {

	/** Whether two scalar fields match exactly, and whether they are within ±1. */
	public record ScalarMatch(boolean exact, boolean withinOne) {
	}

	/** Set-overlap quality for the descriptor list. */
	public record DescriptorScore(double precision, double recall, double f1) {
	}

	/** Per-image, per-model field scores. */
	public record TagScore(
		boolean categoryMatch,
		boolean primaryColorMatch,
		boolean secondaryColorMatch,
		boolean patternMatch,
		ScalarMatch formality,
		ScalarMatch warmth,
		DescriptorScore descriptors) {
	}

	/** Run-level aggregate (percentages 0–100) over {@code n} images. */
	public record Aggregate(
		double categoryPct,
		double primaryColorPct,
		double secondaryColorPct,
		double patternPct,
		double formalityExactPct,
		double formalityWithinOnePct,
		double warmthExactPct,
		double warmthWithinOnePct,
		double meanDescriptorF1,
		int n) {
	}

	private AccuracyScorer() {
	}

	/** Scores one prediction against its gold label. */
	public static TagScore score(TagSuggestion pred, GoldTag gold) {
		return new TagScore(
			stringMatch(pred.category(), gold.category()),
			stringMatch(pred.primaryColor(), gold.primaryColor()),
			stringMatch(pred.secondaryColor(), gold.secondaryColor()),
			stringMatch(pred.pattern(), gold.pattern()),
			scalarMatch(pred.formality(), gold.formality()),
			scalarMatch(pred.warmth(), gold.warmth()),
			descriptorScore(pred.descriptors(), gold.descriptors()));
	}

	static boolean stringMatch(String a, String b) {
		String na = norm(a);
		String nb = norm(b);
		if (na == null || nb == null) {
			return na == null && nb == null;
		}
		return na.equals(nb);
	}

	static ScalarMatch scalarMatch(Integer a, Integer b) {
		if (a == null || b == null) {
			boolean bothNull = a == null && b == null;
			return new ScalarMatch(bothNull, bothNull);
		}
		int diff = Math.abs(a - b);
		return new ScalarMatch(diff == 0, diff <= 1);
	}

	static DescriptorScore descriptorScore(List<String> pred, List<String> gold) {
		Set<String> p = normSet(pred);
		Set<String> g = normSet(gold);
		long tp = p.stream().filter(g::contains).count();
		double precision = p.isEmpty() ? (g.isEmpty() ? 1.0 : 0.0) : (double) tp / p.size();
		double recall = g.isEmpty() ? (p.isEmpty() ? 1.0 : 0.0) : (double) tp / g.size();
		double f1 = (precision + recall == 0.0) ? 0.0 : 2 * precision * recall / (precision + recall);
		return new DescriptorScore(precision, recall, f1);
	}

	/** Aggregates per-image scores into run-level percentages. Empty input yields all-zero. */
	public static Aggregate aggregate(List<TagScore> scores) {
		int n = scores.size();
		if (n == 0) {
			return new Aggregate(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
		double cat = 0, pc = 0, sc = 0, pat = 0, fEx = 0, fW = 0, wEx = 0, wW = 0, f1 = 0;
		for (TagScore s : scores) {
			cat += bit(s.categoryMatch());
			pc += bit(s.primaryColorMatch());
			sc += bit(s.secondaryColorMatch());
			pat += bit(s.patternMatch());
			fEx += bit(s.formality().exact());
			fW += bit(s.formality().withinOne());
			wEx += bit(s.warmth().exact());
			wW += bit(s.warmth().withinOne());
			f1 += s.descriptors().f1();
		}
		return new Aggregate(pct(cat, n), pct(pc, n), pct(sc, n), pct(pat, n),
			pct(fEx, n), pct(fW, n), pct(wEx, n), pct(wW, n), f1 / n, n);
	}

	private static double bit(boolean x) {
		return x ? 1.0 : 0.0;
	}

	private static double pct(double sum, int n) {
		return sum / n * 100.0;
	}

	private static String norm(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim().toLowerCase();
		return t.isEmpty() ? null : t;
	}

	private static Set<String> normSet(List<String> in) {
		Set<String> out = new LinkedHashSet<>();
		if (in != null) {
			for (String s : in) {
				String v = norm(s);
				if (v != null) {
					out.add(v);
				}
			}
		}
		return out;
	}
}
