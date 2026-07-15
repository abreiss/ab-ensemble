package com.ensemble.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic settings bound from {@code ensemble.anthropic.*}.
 *
 * <p>The API key is bound here (via {@code ${ENSEMBLE_ANTHROPIC_API_KEY:}} in
 * {@code application.yml}) but sourced only from the environment / a git-ignored
 * {@code .env} file — never a committed value. A blank/unset key normalizes to
 * {@code null} so {@code AnthropicConfig} falls back to the SDK's own
 * {@code ANTHROPIC_API_KEY} resolution ({@code AnthropicOkHttpClient.fromEnv()}).
 * {@link #toString()} masks the key so it cannot leak into logs.
 *
 * @param model the Claude model id used for vision tagging; defaults to
 *     {@value #DEFAULT_MODEL} when blank/unset so tagging is pinned to Haiku 4.5.
 * @param timeout bounded per-request timeout; a non-positive/unset value falls back
 *     to {@link #DEFAULT_TIMEOUT} so a hung call degrades to the tagging fallback
 *     rather than blocking indefinitely.
 * @param apiKey the Claude API key, supplied via the {@code ENSEMBLE_ANTHROPIC_API_KEY}
 *     variable (read from a git-ignored {@code .env} file or the process environment) and
 *     bound through {@code application.yml}. A blank/unset value is normalized to
 *     {@code null} so {@code AnthropicConfig} falls back to the SDK's own environment
 *     resolution. It is never a committed value — the {@code .env} file is git-ignored.
 * @param stylistModel the Claude model id used for stylist reasoning; defaults to
 *     {@value #DEFAULT_STYLIST_MODEL} when blank/unset so styling is pinned to Sonnet 5.
 *     Kept separate from {@link #model} so tagging (Haiku) and styling (Sonnet) are
 *     configured independently.
 */
@ConfigurationProperties(prefix = "ensemble.anthropic")
public record AnthropicProperties(String model, Duration timeout, String apiKey, String stylistModel) {

	/** Vision tagging is pinned to Haiku 4.5 (see docs/ARCHITECTURE.md). */
	public static final String DEFAULT_MODEL = "claude-haiku-4-5";

	/** Stylist reasoning is pinned to Sonnet 5 (see docs/ARCHITECTURE.md). */
	public static final String DEFAULT_STYLIST_MODEL = "claude-sonnet-5";

	/** Bounded default so a slow/hung call falls back instead of blocking. */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	public AnthropicProperties {
		if (model == null || model.isBlank()) {
			model = DEFAULT_MODEL;
		}
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			timeout = DEFAULT_TIMEOUT;
		}
		if (apiKey != null && apiKey.isBlank()) {
			apiKey = null;
		}
		if (stylistModel == null || stylistModel.isBlank()) {
			stylistModel = DEFAULT_STYLIST_MODEL;
		}
	}

	/**
	 * Masks {@code apiKey} so the secret never lands in a log line or error message.
	 * The default record {@code toString()} would render the raw key verbatim.
	 */
	@Override
	public String toString() {
		return "AnthropicProperties[model=" + model
			+ ", timeout=" + timeout
			+ ", apiKey=" + (apiKey == null ? "null" : "****")
			+ ", stylistModel=" + stylistModel + "]";
	}
}
