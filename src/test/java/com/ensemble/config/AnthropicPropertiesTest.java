package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnthropicPropertiesTest {

	@Test
	void configuredModel_isKept() {
		assertThat(new AnthropicProperties("claude-sonnet-5", Duration.ofSeconds(10)).model())
			.isEqualTo("claude-sonnet-5");
	}

	@Test
	void blankModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties("  ", null).model())
			.isEqualTo(AnthropicProperties.DEFAULT_MODEL);
		assertThat(AnthropicProperties.DEFAULT_MODEL).isEqualTo("claude-haiku-4-5");
	}

	@Test
	void nullModel_fallsBackToHaikuDefault() {
		assertThat(new AnthropicProperties(null, null).model())
			.isEqualTo("claude-haiku-4-5");
	}

	@Test
	void configuredTimeout_isKept() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ofSeconds(45)).timeout())
			.isEqualTo(Duration.ofSeconds(45));
	}

	@Test
	void nullTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", null).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}

	@Test
	void nonPositiveTimeout_fallsBackToDefault() {
		assertThat(new AnthropicProperties("claude-haiku-4-5", Duration.ZERO).timeout())
			.isEqualTo(AnthropicProperties.DEFAULT_TIMEOUT);
	}
}
