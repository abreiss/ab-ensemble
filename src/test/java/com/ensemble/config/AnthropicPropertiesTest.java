package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnthropicPropertiesTest {

	@Test
	void configuredModel_isKept() {
		assertThat(new AnthropicProperties("claude-sonnet-5", Duration.ofSeconds(10), null).model())
			.isEqualTo("claude-sonnet-5");
	}

	@Test
	void blankModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties("  ", null, null).model())
			.isEqualTo(AnthropicProperties.DEFAULT_MODEL);
		assertThat(AnthropicProperties.DEFAULT_MODEL).isEqualTo("claude-haiku-4-5");
	}

	@Test
	void nullModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties(null, null, null).model())
			.isEqualTo("claude-haiku-4-5");
	}

	@Test
	void configuredTimeout_isKept() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ofSeconds(45), null).timeout())
			.isEqualTo(Duration.ofSeconds(45));
	}

	@Test
	void nullTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void nonPositiveTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ZERO, null).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void configuredApiKey_isKept() {
		// A dummy value: never a real sk-ant-* key (keeps the secret scan green).
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, "dummy-key-123").apiKey())
			.isEqualTo("dummy-key-123");
	}

	@Test
	void blankApiKey_normalizesToNull() {
		// Unset in .env resolves to an empty string via ${ENSEMBLE_ANTHROPIC_API_KEY:};
		// normalize it to null so the config falls back to the SDK's env resolution.
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, "   ").apiKey()).isNull();
	}

	@Test
	void nullApiKey_isNull() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null).apiKey()).isNull();
	}
}
