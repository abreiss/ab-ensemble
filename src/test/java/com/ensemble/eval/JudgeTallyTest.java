package com.ensemble.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ensemble.eval.JudgeResultParser.JudgeVerdict;
import com.ensemble.eval.JudgeTally.ImageJudgement;
import com.ensemble.eval.JudgeTally.Tally;

/** Blind A/B verdicts must de-anonymize back to the correct real model, even when A/B is swapped. */
class JudgeTallyTest {

	private static JudgeVerdict winner(String w) {
		return new JudgeVerdict(w, Map.of(), "");
	}

	@Test
	void deAnonymizesAcrossSwappedAssignments() {
		// Image 1: A=haiku, B=sonnet, judge picks A  → haiku win
		// Image 2: A=sonnet, B=haiku, judge picks A  → sonnet win (A is sonnet here!)
		// Image 3: A=haiku, B=sonnet, judge picks B  → sonnet win
		// Image 4: tie
		List<ImageJudgement> js = List.of(
			new ImageJudgement("claude-haiku-4-5", "claude-sonnet-5", winner("A")),
			new ImageJudgement("claude-sonnet-5", "claude-haiku-4-5", winner("A")),
			new ImageJudgement("claude-haiku-4-5", "claude-sonnet-5", winner("B")),
			new ImageJudgement("claude-haiku-4-5", "claude-sonnet-5", winner("tie")));

		Tally t = JudgeTally.tally(js);

		assertThat(t.wins()).containsEntry("claude-haiku-4-5", 1);
		assertThat(t.wins()).containsEntry("claude-sonnet-5", 2);
		assertThat(t.ties()).isEqualTo(1);
	}

	@Test
	void emptyInput_zeroTiesEmptyWins() {
		Tally t = JudgeTally.tally(List.of());
		assertThat(t.wins()).isEmpty();
		assertThat(t.ties()).isZero();
	}
}
