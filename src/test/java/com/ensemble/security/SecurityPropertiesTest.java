package com.ensemble.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

	@Test
	void nullSessionTtl_fallsBackToTwelveHourDefault() {
		assertThat(new SecurityProperties("pw", "secret", null).sessionTtl())
			.isEqualTo(SecurityProperties.DEFAULT_SESSION_TTL);
		assertThat(SecurityProperties.DEFAULT_SESSION_TTL).isEqualTo(Duration.ofHours(12));
	}

	@Test
	void nonPositiveSessionTtl_fallsBackToDefault() {
		assertThat(new SecurityProperties("pw", "secret", Duration.ZERO).sessionTtl())
			.isEqualTo(SecurityProperties.DEFAULT_SESSION_TTL);
		assertThat(new SecurityProperties("pw", "secret", Duration.ofMinutes(-5)).sessionTtl())
			.isEqualTo(SecurityProperties.DEFAULT_SESSION_TTL);
	}

	@Test
	void configuredSessionTtl_isKept() {
		assertThat(new SecurityProperties("pw", "secret", Duration.ofHours(2)).sessionTtl())
			.isEqualTo(Duration.ofHours(2));
	}

	@Test
	void blankSessionSecret_derivesFromPasscode() {
		assertThat(new SecurityProperties("pw", "", null).sessionSecret()).isEqualTo("pw");
		assertThat(new SecurityProperties("pw", "   ", null).sessionSecret()).isEqualTo("pw");
		assertThat(new SecurityProperties("pw", null, null).sessionSecret()).isEqualTo("pw");
	}

	@Test
	void configuredSessionSecret_isKeptIndependentOfPasscode() {
		assertThat(new SecurityProperties("pw", "own-secret", null).sessionSecret())
			.isEqualTo("own-secret");
	}

	@Test
	void nullPasscode_normalizesToBlankAndIsAccepted() {
		SecurityProperties props = new SecurityProperties(null, null, null);
		assertThat(props.passcode()).isEmpty();
		assertThat(props.passcodeConfigured()).isFalse();
	}

	@Test
	void blankPasscode_isAcceptedButFlaggedAsUnconfigured() {
		SecurityProperties props = new SecurityProperties("   ", null, null);
		assertThat(props.passcodeConfigured()).isFalse();
	}

	@Test
	void nonBlankPasscode_isFlaggedAsConfigured() {
		assertThat(new SecurityProperties("pw", null, null).passcodeConfigured()).isTrue();
	}

	@Test
	void toString_masksPasscodeAndSessionSecret() {
		String rendered = new SecurityProperties("super-secret-pw", "super-secret-key", Duration.ofHours(6))
			.toString();
		assertThat(rendered).doesNotContain("super-secret-pw");
		assertThat(rendered).doesNotContain("super-secret-key");
		assertThat(rendered).contains("PT6H");
	}
}
