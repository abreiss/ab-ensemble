package com.ensemble.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Pricing table + Sonnet introductory-window boundary + cost arithmetic. */
class CostCalculatorTest {

	private static final LocalDate DURING_INTRO = LocalDate.of(2026, 7, 15);

	@Test
	void haiku_flatRate() {
		CostCalculator.Rate r = CostCalculator.rateFor("claude-haiku-4-5", DURING_INTRO);
		assertThat(r.inputPerMillion()).isEqualTo(1.0);
		assertThat(r.outputPerMillion()).isEqualTo(5.0);
	}

	@Test
	void opus_flatRate() {
		CostCalculator.Rate r = CostCalculator.rateFor("claude-opus-4-8", DURING_INTRO);
		assertThat(r.inputPerMillion()).isEqualTo(5.0);
		assertThat(r.outputPerMillion()).isEqualTo(25.0);
	}

	@Test
	void sonnet_introPricing_onOrBeforeCutoff() {
		CostCalculator.Rate before = CostCalculator.rateFor("claude-sonnet-5", DURING_INTRO);
		CostCalculator.Rate onCutoff = CostCalculator.rateFor("claude-sonnet-5", CostCalculator.SONNET_INTRO_UNTIL);
		assertThat(before.inputPerMillion()).isEqualTo(2.0);
		assertThat(before.outputPerMillion()).isEqualTo(10.0);
		assertThat(onCutoff.inputPerMillion()).isEqualTo(2.0);
		assertThat(onCutoff.outputPerMillion()).isEqualTo(10.0);
	}

	@Test
	void sonnet_stickerPricing_afterCutoff() {
		CostCalculator.Rate after = CostCalculator.rateFor("claude-sonnet-5", CostCalculator.SONNET_INTRO_UNTIL.plusDays(1));
		assertThat(after.inputPerMillion()).isEqualTo(3.0);
		assertThat(after.outputPerMillion()).isEqualTo(15.0);
	}

	@Test
	void costUsd_multipliesTokensByRate() {
		// Haiku: 1,000,000 in * $1/M + 200,000 out * $5/M = 1.00 + 1.00 = 2.00
		double cost = CostCalculator.costUsd("claude-haiku-4-5", 1_000_000L, 200_000L, DURING_INTRO);
		assertThat(cost).isEqualTo(2.0);
	}

	@Test
	void unknownModel_throws() {
		assertThatThrownBy(() -> CostCalculator.rateFor("gpt-9", DURING_INTRO))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("gpt-9");
	}
}
