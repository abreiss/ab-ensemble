package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SeedProperties} (issue #14): null-normalization, the
 * {@code configured()} decision, and that {@link SeedProperties#toString()} never
 * echoes the raw username/password.
 */
class SeedPropertiesTest {

	@Test
	void nullUsernameAndPassword_normalizeToBlank() {
		SeedProperties props = new SeedProperties(null, null);

		assertThat(props.username()).isEmpty();
		assertThat(props.password()).isEmpty();
		assertThat(props.configured()).isFalse();
	}

	@Test
	void bothSet_isConfigured() {
		assertThat(new SeedProperties("seed_user", "seed-password").configured()).isTrue();
	}

	@Test
	void onlyUsernameSet_isNotConfigured() {
		assertThat(new SeedProperties("seed_user", "").configured()).isFalse();
		assertThat(new SeedProperties("seed_user", "   ").configured()).isFalse();
	}

	@Test
	void onlyPasswordSet_isNotConfigured() {
		assertThat(new SeedProperties("", "seed-password").configured()).isFalse();
		assertThat(new SeedProperties(null, "seed-password").configured()).isFalse();
	}

	@Test
	void toString_masksUsernameAndPassword() {
		String rendered = new SeedProperties("seed_user", "super-secret-pw").toString();

		assertThat(rendered).doesNotContain("seed_user");
		assertThat(rendered).doesNotContain("super-secret-pw");
	}

	@Test
	void toString_blankValues_maskedAsEmpty() {
		String rendered = new SeedProperties("", "").toString();

		assertThat(rendered).contains("****(empty)");
	}
}
