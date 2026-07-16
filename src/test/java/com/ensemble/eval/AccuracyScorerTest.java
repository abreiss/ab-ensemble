package com.ensemble.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ensemble.eval.AccuracyScorer.Aggregate;
import com.ensemble.eval.AccuracyScorer.DescriptorScore;
import com.ensemble.eval.AccuracyScorer.ScalarMatch;
import com.ensemble.eval.AccuracyScorer.TagScore;
import com.ensemble.tagging.dto.TagSuggestion;

/** Per-field scoring rules and run-level aggregation. */
class AccuracyScorerTest {

	private static GoldTag gold(String cat, String pc, String sc, Integer form, String pat, Integer warm, List<String> desc) {
		return new GoldTag("x.jpg", cat, pc, sc, form, pat, warm, desc);
	}

	private static TagSuggestion pred(String cat, String pc, String sc, Integer form, String pat, Integer warm, List<String> desc) {
		return new TagSuggestion(cat, pc, sc, form, pat, warm, desc);
	}

	@Test
	void perfectMatch_allFieldsTrue_descriptorF1One() {
		GoldTag g = gold("top", "black", "white", 2, "graphic", 1, List.of("band-tee", "cotton"));
		TagScore s = AccuracyScorer.score(
			pred("top", "black", "white", 2, "graphic", 1, List.of("band-tee", "cotton")), g);

		assertThat(s.categoryMatch()).isTrue();
		assertThat(s.primaryColorMatch()).isTrue();
		assertThat(s.secondaryColorMatch()).isTrue();
		assertThat(s.patternMatch()).isTrue();
		assertThat(s.formality()).isEqualTo(new ScalarMatch(true, true));
		assertThat(s.warmth()).isEqualTo(new ScalarMatch(true, true));
		assertThat(s.descriptors().f1()).isEqualTo(1.0);
	}

	@Test
	void stringMatch_isCaseAndWhitespaceInsensitive() {
		assertThat(AccuracyScorer.stringMatch("  Black ", "black")).isTrue();
		assertThat(AccuracyScorer.stringMatch("navy", "black")).isFalse();
	}

	@Test
	void stringMatch_bothNullAgree_oneSidedNullMismatches() {
		assertThat(AccuracyScorer.stringMatch(null, null)).isTrue();
		assertThat(AccuracyScorer.stringMatch(null, "black")).isFalse();
		assertThat(AccuracyScorer.stringMatch("black", null)).isFalse();
		// Blank normalizes to null, so blank-vs-null also agrees.
		assertThat(AccuracyScorer.stringMatch("   ", null)).isTrue();
	}

	@Test
	void scalarMatch_exactWithinOneAndOff() {
		assertThat(AccuracyScorer.scalarMatch(3, 3)).isEqualTo(new ScalarMatch(true, true));
		assertThat(AccuracyScorer.scalarMatch(2, 3)).isEqualTo(new ScalarMatch(false, true));
		assertThat(AccuracyScorer.scalarMatch(1, 3)).isEqualTo(new ScalarMatch(false, false));
	}

	@Test
	void scalarMatch_nullHandling() {
		assertThat(AccuracyScorer.scalarMatch(null, null)).isEqualTo(new ScalarMatch(true, true));
		assertThat(AccuracyScorer.scalarMatch(null, 3)).isEqualTo(new ScalarMatch(false, false));
		assertThat(AccuracyScorer.scalarMatch(3, null)).isEqualTo(new ScalarMatch(false, false));
	}

	@Test
	void descriptorScore_partialOverlap() {
		// pred {a,b,c}, gold {b,c,d}: TP=2 → P=2/3, R=2/3, F1=2/3
		DescriptorScore d = AccuracyScorer.descriptorScore(List.of("a", "b", "c"), List.of("b", "c", "d"));
		assertThat(d.precision()).isCloseTo(2.0 / 3, within(1e-9));
		assertThat(d.recall()).isCloseTo(2.0 / 3, within(1e-9));
		assertThat(d.f1()).isCloseTo(2.0 / 3, within(1e-9));
	}

	@Test
	void descriptorScore_emptyEdgeCases() {
		assertThat(AccuracyScorer.descriptorScore(List.of(), List.of()).f1()).isEqualTo(1.0);
		assertThat(AccuracyScorer.descriptorScore(List.of("a"), List.of()).f1()).isEqualTo(0.0);
		assertThat(AccuracyScorer.descriptorScore(List.of(), List.of("a")).f1()).isEqualTo(0.0);
		assertThat(AccuracyScorer.descriptorScore(null, null).f1()).isEqualTo(1.0);
	}

	@Test
	void aggregate_computesPercentagesAcrossImages() {
		// Image 1: category hit, formality exact. Image 2: category miss, formality within-one only.
		TagScore s1 = AccuracyScorer.score(
			pred("top", null, null, 3, null, null, null),
			gold("top", null, null, 3, null, null, null));
		TagScore s2 = AccuracyScorer.score(
			pred("bottom", null, null, 2, null, null, null),
			gold("top", null, null, 3, null, null, null));

		Aggregate agg = AccuracyScorer.aggregate(List.of(s1, s2));

		assertThat(agg.n()).isEqualTo(2);
		assertThat(agg.categoryPct()).isEqualTo(50.0);        // 1 of 2
		assertThat(agg.formalityExactPct()).isEqualTo(50.0);  // s1 exact, s2 not
		assertThat(agg.formalityWithinOnePct()).isEqualTo(100.0); // both within ±1
	}

	@Test
	void aggregate_emptyIsZero() {
		assertThat(AccuracyScorer.aggregate(List.of()).n()).isZero();
	}
}
