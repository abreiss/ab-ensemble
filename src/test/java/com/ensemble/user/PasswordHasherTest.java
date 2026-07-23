package com.ensemble.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PasswordHasher}. No Spring context — the component is a
 * thin wrapper over {@code BCryptPasswordEncoder(12)}, so it is exercised
 * directly. Covers both branches of {@link PasswordHasher#matches} (right vs
 * wrong password) and proves storage is salted (same input → different hashes).
 */
class PasswordHasherTest {

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	void hashVerifiesAgainstRawPassword() {
		String raw = "correcthorsebatterystaple";

		String hash = hasher.hash(raw);

		assertThat(hash).isNotEqualTo(raw);
		assertThat(hasher.matches(raw, hash)).isTrue();
	}

	@Test
	void wrongPasswordDoesNotMatch() {
		String hash = hasher.hash("correcthorsebatterystaple");

		assertThat(hasher.matches("Tr0ub4dour", hash)).isFalse();
	}

	@Test
	void sameInputProducesDifferentSalts() {
		String raw = "correcthorsebatterystaple";

		String first = hasher.hash(raw);
		String second = hasher.hash(raw);

		// A per-hash random salt means identical input yields distinct hashes,
		// yet both still verify against the raw password.
		assertThat(first).isNotEqualTo(second);
		assertThat(hasher.matches(raw, first)).isTrue();
		assertThat(hasher.matches(raw, second)).isTrue();
	}
}
