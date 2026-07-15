package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnthropicPropertiesTest {

	@Test
	void configuredModel_isKept() {
		assertThat(new AnthropicProperties("claude-sonnet-5", Duration.ofSeconds(10), null, null).model())
			.isEqualTo("claude-sonnet-5");
	}

	@Test
	void blankModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties("  ", null, null, null).model())
			.isEqualTo(AnthropicProperties.DEFAULT_MODEL);
		assertThat(AnthropicProperties.DEFAULT_MODEL).isEqualTo("claude-haiku-4-5");
	}

	@Test
	void nullModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties(null, null, null, null).model())
			.isEqualTo("claude-haiku-4-5");
	}

	@Test
	void configuredTimeout_isKept() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ofSeconds(45), null, null).timeout())
			.isEqualTo(Duration.ofSeconds(45));
	}

	@Test
	void nullTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, null).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void nonPositiveTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ZERO, null, null).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void configuredApiKey_isKept() {
		// A dummy value: never a real sk-ant-* key (keeps the secret scan green).
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, "dummy-key-123", null).apiKey())
			.isEqualTo("dummy-key-123");
	}

	@Test
	void blankApiKey_normalizesToNull() {
		// Unset in .env resolves to an empty string via ${ENSEMBLE_ANTHROPIC_API_KEY:};
		// normalize it to null so the config falls back to the SDK's env resolution.
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, "   ", null).apiKey()).isNull();
	}

	@Test
	void nullApiKey_isNull() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, null).apiKey()).isNull();
	}

	@Test
	void configuredStylistModel_isKept() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, "claude-sonnet-5").stylistModel())
			.isEqualTo("claude-sonnet-5");
	}

	@Test
	void blankStylistModel_fallsBackToSonnetDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, "  ").stylistModel())
			.isEqualTo(AnthropicProperties.DEFAULT_STYLIST_MODEL);
		assertThat(AnthropicProperties.DEFAULT_STYLIST_MODEL).isEqualTo("claude-sonnet-5");
	}

	@Test
	void nullStylistModel_fallsBackToSonnetDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, null).stylistModel())
			.isEqualTo("claude-sonnet-5");
	}

	@Test
	void apiKey_isMaskedInToString() {
		// The record binds the key as a component, so the default toString() would leak it.
		// A dummy value: never a real sk-ant-* key (keeps the secret scan green).
		String rendered = new AnthropicProperties("claude-haiku-4-5", null, "dummy-key-123", null).toString();
		assertThat(rendered).doesNotContain("dummy-key-123");
		// Non-secret fields stay visible so the record is still useful in logs.
		assertThat(rendered).contains("claude-haiku-4-5");
	}

	@Test
	void nullApiKey_rendersNullInToString() {
		// Exercises the null branch of the masked toString().
		assertThat(new AnthropicProperties("claude-haiku-4-5", null, null, null).toString())
			.contains("apiKey=null");
	}
}
