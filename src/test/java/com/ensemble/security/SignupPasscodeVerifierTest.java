package com.ensemble.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Unit test for the constant-time signup-passcode check (issue #14). Proves the four
 * decision branches of {@link SignupPasscodeVerifier#matches}: a correct candidate matches,
 * a wrong one does not, a null/blank candidate is rejected outright, and a blank
 * <em>configured</em> passcode closes signup entirely (no candidate can match). Drives 100%
 * branch coverage on the gate.
 */
class SignupPasscodeVerifierTest {

	private static SignupPasscodeVerifier verifierWithPasscode(String configured) {
		return new SignupPasscodeVerifier(new SecurityProperties(configured, null, Duration.ofHours(12)));
	}

	@Test
	void matchesConfiguredPasscode() {
		// Arrange
		SignupPasscodeVerifier verifier = verifierWithPasscode("open-sesame");

		// Act
		boolean result = verifier.matches("open-sesame");

		// Assert
		assertThat(result).isTrue();
	}

	@Test
	void rejectsWrongPasscode() {
		// Arrange
		SignupPasscodeVerifier verifier = verifierWithPasscode("open-sesame");

		// Act
		boolean result = verifier.matches("not-the-passcode");

		// Assert
		assertThat(result).isFalse();
	}

	@Test
	void rejectsBlankOrNullCandidate() {
		// Arrange
		SignupPasscodeVerifier verifier = verifierWithPasscode("open-sesame");

		// Act + Assert: a null, empty, or whitespace-only candidate is rejected before any compare.
		assertThat(verifier.matches(null)).isFalse();
		assertThat(verifier.matches("")).isFalse();
		assertThat(verifier.matches("   ")).isFalse();
	}

	@Test
	void signupClosedWhenPasscodeUnconfigured() {
		// Arrange: a blank configured passcode means registration is closed.
		SignupPasscodeVerifier verifier = verifierWithPasscode("");

		// Act + Assert: no candidate — not even a blank one — can open a closed gate.
		assertThat(verifier.matches("anything")).isFalse();
		assertThat(verifier.matches("")).isFalse();
	}
}
