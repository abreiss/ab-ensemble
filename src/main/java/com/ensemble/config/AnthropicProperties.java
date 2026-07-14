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
 */
@ConfigurationProperties(prefix = "ensemble.anthropic")
public record AnthropicProperties(String model, Duration timeout) {

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
	}
}
