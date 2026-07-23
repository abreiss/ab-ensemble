package com.ensemble.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

/**
 * Verifies the shared signup passcode that keeps account registration invite-only (issue #14).
 * The candidate submitted to {@code POST /api/accounts} is compared against the configured
 * passcode ({@code ensemble.security.passcode} / {@code ENSEMBLE_PASSCODE}) with the same
 * <strong>constant-time</strong> SHA-256 comparison the old shared-passcode login used, factored
 * out here so both length and content are fixed-time.
 *
 * <p>A null or blank candidate is rejected outright, and a <strong>blank configured passcode
 * closes signup entirely</strong> — no candidate can match — so a misconfigured server never
 * accidentally lets strangers register. On a mismatch the caller throws
 * {@link InvalidPasscodeException} (mapped to a generic {@code 401}), never revealing whether the
 * gate is even configured.
 */
@Component
public class SignupPasscodeVerifier {

	private final SecurityProperties properties;

	public SignupPasscodeVerifier(SecurityProperties properties) {
		this.properties = properties;
	}

	/**
	 * Constant-time comparison of {@code candidate} against the configured signup passcode. Both
	 * values are hashed first so the comparison is fixed-length regardless of input length. Returns
	 * {@code false} for a null/blank candidate and whenever no passcode is configured (signup
	 * closed), so a blank guess can never match a blank/unconfigured server passcode.
	 */
	public boolean matches(String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		if (!properties.passcodeConfigured()) {
			return false;
		}
		return MessageDigest.isEqual(sha256(properties.passcode()), sha256(candidate));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
