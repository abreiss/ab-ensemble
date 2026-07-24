package com.ensemble.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ensemble.config.ClockConfig;

/**
 * Pins the fail-closed guard in {@link SecurityConfig}: a blank
 * {@code ENSEMBLE_SESSION_SECRET} under the {@code cloud} profile must abort startup
 * (the token-signing key would otherwise fall back to the shared invite code, letting
 * any invited user forge tokens for arbitrary userIds — see issue #35). Local dev keeps
 * the passcode-derived fallback and only warns, so the fallback stays usable off-cloud.
 *
 * <p>Mirrors the {@link com.ensemble.config.CloudProfileConfigTest}
 * {@code ApplicationContextRunner} + {@code ConfigDataApplicationContextInitializer}
 * pattern. {@link ClockConfig} supplies the {@link java.time.Clock} the
 * {@code sessionTokenService} bean needs, so the <em>only</em> possible startup failure
 * is the guard under test.
 */
class SecurityConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(SecurityConfig.class, ClockConfig.class);

	@Test
	void cloudProfile_blankSessionSecret_failsToStart() {
		runner.withPropertyValues("spring.profiles.active=cloud", "ensemble.security.session-secret=")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context).getFailure().hasStackTraceContaining("ENSEMBLE_SESSION_SECRET");
			});
	}

	@Test
	void cloudProfile_configuredSessionSecret_startsCleanly() {
		runner.withPropertyValues(
				"spring.profiles.active=cloud",
				"ensemble.security.session-secret=a-distinct-signing-secret")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void localProfile_blankSessionSecret_startsWithFallback() {
		runner.withPropertyValues("ensemble.security.session-secret=")
			.run(context -> assertThat(context).hasNotFailed());
	}

	// Covers the passcode-configured (no-warn) side of logGateState()'s passcode branch —
	// the other tests in this class all leave the passcode blank and only exercise the warn side.
	@Test
	void localProfile_configuredPasscode_startsCleanlyWithoutWarning() {
		runner.withPropertyValues(
				"ensemble.security.passcode=demo-invite-code",
				"ensemble.security.session-secret=")
			.run(context -> assertThat(context).hasNotFailed());
	}
}
