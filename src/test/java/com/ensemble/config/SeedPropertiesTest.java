package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SeedProperties} (issue #14): null-normalization, the
 * {@code configured()} decision, and that {@link SeedProperties#toString()} never
 * echoes the raw email/password.
 */
class SeedPropertiesTest {

	@Test
	void nullEmailAndPassword_normalizeToBlank() {
		SeedProperties props = new SeedProperties(null, null);

		assertThat(props.email()).isEmpty();
		assertThat(props.password()).isEmpty();
		assertThat(props.configured()).isFalse();
	}

	@Test
	void bothSet_isConfigured() {
		assertThat(new SeedProperties("seed@example.com", "seed-password").configured()).isTrue();
	}

	@Test
	void onlyEmailSet_isNotConfigured() {
		assertThat(new SeedProperties("seed@example.com", "").configured()).isFalse();
		assertThat(new SeedProperties("seed@example.com", "   ").configured()).isFalse();
	}

	@Test
	void onlyPasswordSet_isNotConfigured() {
		assertThat(new SeedProperties("", "seed-password").configured()).isFalse();
		assertThat(new SeedProperties(null, "seed-password").configured()).isFalse();
	}

	@Test
	void toString_masksEmailAndPassword() {
		String rendered = new SeedProperties("seed@example.com", "super-secret-pw").toString();

		assertThat(rendered).doesNotContain("seed@example.com");
		assertThat(rendered).doesNotContain("super-secret-pw");
	}

	@Test
	void toString_blankValues_maskedAsEmpty() {
		String rendered = new SeedProperties("", "").toString();

		assertThat(rendered).contains("****(empty)");
	}
}
