package com.ensemble.eval;

import java.time.LocalDate;

/**
 * Token → USD cost for the model eval, with the Claude pricing the experiment cares about
 * baked in (per 1M tokens): Haiku 4.5 $1/$5, Sonnet 5 $3/$15 sticker with a $2/$10
 * introductory window through {@link #SONNET_INTRO_UNTIL}, Opus 4.8 (judge) $5/$25.
 *
 * <p>Pure and side-effect-free so the rate table and the intro-date boundary are unit-tested
 * without any network. Part of the offline eval harness only.
 */
public final class CostCalculator {

	/** Price in USD per 1,000,000 tokens. */
	public record Rate(double inputPerMillion, double outputPerMillion) {
	}

	/** Sonnet 5 introductory pricing applies through this date, inclusive. */
	public static final LocalDate SONNET_INTRO_UNTIL = LocalDate.of(2026, 8, 31);

	private CostCalculator() {
	}

	/**
	 * The USD-per-million rate for a model on a given date. Matching is by substring so the
	 * bare aliases ({@code claude-haiku-4-5}, {@code claude-sonnet-5}, {@code claude-opus-4-8})
	 * all resolve.
	 *
	 * @throws IllegalArgumentException if no rate is known for the model
	 */
	public static Rate rateFor(String model, LocalDate onDate) {
		String m = model == null ? "" : model.toLowerCase();
		if (m.contains("haiku-4-5")) {
			return new Rate(1.0, 5.0);
		}
		if (m.contains("sonnet-5")) {
			boolean intro = !onDate.isAfter(SONNET_INTRO_UNTIL);
			return intro ? new Rate(2.0, 10.0) : new Rate(3.0, 15.0);
		}
		if (m.contains("opus-4-8")) {
			return new Rate(5.0, 25.0);
		}
		throw new IllegalArgumentException("no pricing configured for model: " + model);
	}

	/** Total USD for a single call's input + output tokens on the given date. */
	public static double costUsd(String model, long inputTokens, long outputTokens, LocalDate onDate) {
		Rate rate = rateFor(model, onDate);
		return inputTokens / 1_000_000.0 * rate.inputPerMillion()
			+ outputTokens / 1_000_000.0 * rate.outputPerMillion();
	}
}
