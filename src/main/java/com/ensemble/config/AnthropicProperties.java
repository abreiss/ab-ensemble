package com.ensemble.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Non-secret Anthropic settings. Bound from {@code ensemble.anthropic.*}.
 *
 * <p>The API key is deliberately <strong>not</strong> a property here — it is read
 * from the {@code ANTHROPIC_API_KEY} environment variable by the SDK
 * ({@code AnthropicOkHttpClient.fromEnv()}), so it is never committed to config.
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
 */
@ConfigurationProperties(prefix = "ensemble.anthropic")
public record AnthropicProperties(String model, Duration timeout, String apiKey) {

	/** Vision tagging is pinned to Haiku 4.5 (see docs/ARCHITECTURE.md). */
	public static final String DEFAULT_MODEL = "claude-haiku-4-5";

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
	}
}
