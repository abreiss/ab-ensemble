package com.ensemble.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One-time default account settings bound from {@code ensemble.seed.*} (issue #14).
 *
 * <p>Both {@code email} and {@code password} are sourced only from the environment
 * (via {@code ${ENSEMBLE_SEED_EMAIL:}} / {@code ${ENSEMBLE_SEED_PASSWORD:}}) — never
 * a committed value. A blank/unset field normalizes to an empty string rather than
 * throwing; {@link #configured()} reports whether <em>both</em> are set, since a
 * half-configured seed (one set, one blank) is treated as not configured at all
 * rather than as a partial attempt. {@link #toString()} masks both values so
 * neither leaks into logs.
 *
 * @param email the default account's email; blank/null normalizes to {@code ""}.
 * @param password the default account's raw password; blank/null normalizes to {@code ""}.
 */
@ConfigurationProperties(prefix = "ensemble.seed")
public record SeedProperties(String email, String password) {

	public SeedProperties {
		if (email == null) {
			email = "";
		}
		if (password == null) {
			password = "";
		}
	}

	/** True only when both {@link #email} and {@link #password} are non-blank. */
	public boolean configured() {
		return !email.isBlank() && !password.isBlank();
	}

	/** Masks {@code email} and {@code password} so neither leaks into a log line. */
	@Override
	public String toString() {
		return "SeedProperties[email=" + mask(email) + ", password=" + mask(password) + "]";
	}

	private static String mask(String value) {
		return value.isEmpty() ? "****(empty)" : "****";
	}
}
