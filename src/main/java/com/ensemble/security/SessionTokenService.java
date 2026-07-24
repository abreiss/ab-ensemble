package com.ensemble.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints and verifies stateless, signed <em>identity-bearing</em> session tokens (issue #14):
 * {@code base64url(payload)} + {@code "."} + {@code base64url(HMAC_SHA256(payload, key))},
 * where {@code payload} is the UTF-8 string {@code userId + ":" + expiryEpochSeconds}. The
 * token therefore carries <strong>who</strong> the caller is (an opaque {@code userId} — no
 * username/PII) alongside <strong>until when</strong> it is valid. Pure and Spring-free by
 * design (see docs/specs/07) — {@code com.ensemble.security.SecurityConfig} wires it as a
 * bean using an injected {@link Clock} so expiry is deterministically testable.
 *
 * <p>Tokens minted under the old identity-less format (payload = bare epoch, no
 * {@code "userId:"} prefix) no longer {@link #verify} — the missing separator is rejected —
 * so callers simply re-authenticate. Tokens are short-lived, so no migration is needed.
 */
public class SessionTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final char USER_ID_SEPARATOR = ':';

	private final SecurityProperties properties;
	private final Clock clock;

	public SessionTokenService(SecurityProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Mints a token for {@code userId} that expires {@code sessionTtl} from now (per the
	 * injected clock). The {@code userId} is embedded in the signed payload and returned
	 * by {@link #verify}.
	 */
	public String issue(String userId) {
		long expiryEpochSecond = clock.instant().plus(properties.sessionTtl()).getEpochSecond();
		byte[] payload = (userId + USER_ID_SEPARATOR + expiryEpochSecond).getBytes(StandardCharsets.UTF_8);
		return encode(payload) + "." + encode(hmac(payload));
	}

	/**
	 * Returns the embedded {@code userId} only for a well-formed token: exactly one
	 * {@code "."}, both segments valid base64url, the signature matches (constant-time),
	 * the payload splits into a non-empty {@code userId} and a numeric expiry, and that
	 * expiry is in the future. Any other shape — null, malformed, tampered, expired,
	 * missing/empty userId (incl. the old identity-less format), or a non-numeric expiry —
	 * is unauthenticated and yields {@link Optional#empty()}.
	 */
	public Optional<String> verify(String token) {
		if (token == null) {
			return Optional.empty();
		}
		int firstDot = token.indexOf('.');
		if (firstDot < 0 || token.indexOf('.', firstDot + 1) >= 0) {
			return Optional.empty();
		}
		byte[] payload;
		byte[] signature;
		try {
			payload = decode(token.substring(0, firstDot));
			signature = decode(token.substring(firstDot + 1));
		}
		catch (IllegalArgumentException malformedBase64) {
			return Optional.empty();
		}
		if (!MessageDigest.isEqual(hmac(payload), signature)) {
			return Optional.empty();
		}
		String decoded = new String(payload, StandardCharsets.UTF_8);
		int separator = decoded.lastIndexOf(USER_ID_SEPARATOR);
		if (separator <= 0) {
			// No separator (old identity-less token) or an empty userId segment.
			return Optional.empty();
		}
		long expiryEpochSecond;
		try {
			expiryEpochSecond = Long.parseLong(decoded.substring(separator + 1));
		}
		catch (NumberFormatException notAnEpoch) {
			return Optional.empty();
		}
		if (!clock.instant().isBefore(Instant.ofEpochSecond(expiryEpochSecond))) {
			return Optional.empty();
		}
		return Optional.of(decoded.substring(0, separator));
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
