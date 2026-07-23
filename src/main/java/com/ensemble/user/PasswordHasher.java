package com.ensemble.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashes and verifies user passwords with bcrypt (work factor 12, above the
 * OWASP minimum). A thin {@code @Component} over {@link BCryptPasswordEncoder}
 * so account code depends on this domain seam, never on the crypto library
 * directly.
 *
 * <p>bcrypt embeds a per-hash random salt in the output, so the same raw
 * password hashes to a different value each time and hashes are compared with
 * {@link #matches} (constant-time within the encoder), never by string equality.
 * The raw password and the hash are never logged.
 */
@Component
public class PasswordHasher {

	private static final int WORK_FACTOR = 12;

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(WORK_FACTOR);

	/** Returns a salted bcrypt hash of the raw password, safe to store at rest. */
	public String hash(String raw) {
		return encoder.encode(raw);
	}

	/** True iff the raw password verifies against the stored bcrypt hash. */
	public boolean matches(String raw, String hash) {
		return encoder.matches(raw, hash);
	}
}
