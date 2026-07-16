package com.ensemble.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints and verifies stateless, signed session tokens: {@code base64url(payload)} +
 * {@code "."} + {@code base64url(HMAC_SHA256(payload, key))}, where {@code payload} is
 * the UTF-8 decimal epoch-second expiry. Pure and Spring-free by design (see
 * docs/specs/07) — {@code com.ensemble.security.SecurityConfig} wires it as a bean using
 * an injected {@link Clock} so expiry is deterministically testable.
 */
public class SessionTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final SecurityProperties properties;
	private final Clock clock;

	public SessionTokenService(SecurityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** Mints a token that expires {@code sessionTtl} from now (per the injected clock). */
	public String issue() {
		long expiryEpochSecond = clock.instant().plus(properties.sessionTtl()).getEpochSecond();
		byte[] payload = Long.toString(expiryEpochSecond).getBytes(StandardCharsets.UTF_8);
		return encode(payload) + "." + encode(hmac(payload));
	}

	/**
	 * True only for a well-formed token: exactly one {@code "."}, both segments valid
	 * base64url, the signature matches (constant-time), and the encoded expiry is in the
	 * future. Any other shape — null, malformed, tampered, or expired — is unauthenticated.
	 */
	public boolean verify(String token) {
		if (token == null) {
			return false;
		}
		int firstDot = token.indexOf('.');
		if (firstDot < 0 || token.indexOf('.', firstDot + 1) >= 0) {
			return false;
		}
		byte[] payload;
		byte[] signature;
		try {
			payload = decode(token.substring(0, firstDot));
			signature = decode(token.substring(firstDot + 1));
		}
		catch (IllegalArgumentException malformedBase64) {
			return false;
		}
		if (!MessageDigest.isEqual(hmac(payload), signature)) {
			return false;
		}
		long expiryEpochSecond;
		try {
			expiryEpochSecond = Long.parseLong(new String(payload, StandardCharsets.UTF_8));
		}
		catch (NumberFormatException notAnEpoch) {
			return false;
		}
		return clock.instant().isBefore(Instant.ofEpochSecond(expiryEpochSecond));
	}

	private byte[] hmac(byte[] data) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(properties.sessionSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return mac.doFinal(data);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
		}
	}

	private static String encode(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] decode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}
}
