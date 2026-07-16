package com.ensemble.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ensemble.eval.JudgeResultParser.JudgeVerdict;

/** Defensive parsing of the judge's structured verdict. */
class JudgeResultParserTest {

	@Test
	void parsesWinnerPerFieldAndReason() {
		String json = """
			{"winner":"A","perField":{"category":"A","primaryColor":"tie"},"reason":"A nailed the color"}""";

		JudgeVerdict v = JudgeResultParser.parse(json);

		assertThat(v.winner()).isEqualTo("A");
		assertThat(v.perField()).containsEntry("category", "A").containsEntry("primaryColor", "tie");
		assertThat(v.reason()).isEqualTo("A nailed the color");
	}

	@Test
	void normalizesWinnerCasingAndAcceptsTie() {
		assertThat(JudgeResultParser.parse("{\"winner\":\"b\"}").winner()).isEqualTo("B");
		assertThat(JudgeResultParser.parse("{\"winner\":\"TIE\"}").winner()).isEqualTo("tie");
	}

	@Test
	void missingPerFieldAndReason_degradeToEmpty() {
		JudgeVerdict v = JudgeResultParser.parse("{\"winner\":\"A\"}");
		assertThat(v.perField()).isEmpty();
		assertThat(v.reason()).isEmpty();
	}

	@Test
	void blankInput_throws() {
		assertThatThrownBy(() -> JudgeResultParser.parse("  "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void malformedJson_throws() {
		assertThatThrownBy(() -> JudgeResultParser.parse("{not json"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missingWinner_throws() {
		assertThatThrownBy(() -> JudgeResultParser.parse("{\"reason\":\"x\"}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("winner");
	}

	@Test
	void unexpectedWinnerValue_throws() {
		assertThatThrownBy(() -> JudgeResultParser.parse("{\"winner\":\"C\"}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("C");
	}
}
