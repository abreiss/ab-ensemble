package com.ensemble.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Passcode-gate settings bound from {@code ensemble.security.*}.
 *
 * <p>{@code passcode} is sourced only from the environment / a git-ignored {@code .env}
 * file (via {@code ${ENSEMBLE_PASSCODE:}}) — never a committed value. A blank/unset
 * passcode normalizes to an empty string rather than throwing; {@link #passcodeConfigured()}
 * reports that state so the caller (startup logging, the auth check) can treat the gate
 * as effectively closed. {@code sessionSecret} derives from the passcode when blank, so a
 * single {@code ENSEMBLE_PASSCODE} env var is enough to run locally. {@link #toString()}
 * masks both secrets so they cannot leak into logs.
 *
 * @param passcode the shared demo passcode; blank/null normalizes to {@code ""}.
 * @param sessionSecret the HMAC key for session tokens; when blank/unset it is derived
 *     from {@link #passcode}.
 * @param sessionTtl session token time-to-live; a null/non-positive value falls back to
 *     {@link #DEFAULT_SESSION_TTL}.
 */
@ConfigurationProperties(prefix = "ensemble.security")
public record SecurityProperties(String passcode, String sessionSecret, Duration sessionTtl) {

	/** Default session TTL when unset (see docs/specs/07 Open Question #2). */
	public static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(12);

	public SecurityProperties {
		if (passcode == null) {
			passcode = "";
		}
		if (sessionSecret == null || sessionSecret.isBlank()) {
			sessionSecret = passcode;
		}
		if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
			sessionTtl = DEFAULT_SESSION_TTL;
		}
	}

	/**
	 * False when no passcode is configured — the gate is then effectively closed (every
	 * passcode check fails, since a blank submission is always rejected first).
	 */
	public boolean passcodeConfigured() {
		return !passcode.isBlank();
	}

	/** Masks {@code passcode} and {@code sessionSecret} so neither leaks into a log line. */
	@Override
	public String toString() {
		return "SecurityProperties[passcode=" + mask(passcode)
			+ ", sessionSecret=" + mask(sessionSecret)
			+ ", sessionTtl=" + sessionTtl + "]";
	}

	private static String mask(String value) {
		return value.isEmpty() ? "****(empty)" : "****";
	}
}
