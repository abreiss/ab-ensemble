package com.ensemble.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * Exercises the identity-bearing session token (issue #14). {@code issue(userId)} mints a
 * token whose signed payload is {@code userId + ":" + expiryEpochSeconds}, and
 * {@code verify(token)} returns the embedded {@code userId} only for a well-formed,
 * untampered, unexpired token — {@code Optional.empty()} for anything else. The malformed,
 * tampered, expired, non-numeric-expiry, missing-userId, and old-identity-less cases give
 * 100% branch on the verify/parse critical logic.
 */
class SessionTokenServiceTest {

	private static final Instant FIXED_NOW = Instant.parse("2026-07-16T12:00:00Z");
	private static final String SECRET = "demo-secret";
	private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

	private SessionTokenService serviceAt(Instant now) {
		SecurityProperties props = new SecurityProperties("demo-pw", SECRET, Duration.ofHours(12));
		return new SessionTokenService(props, Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void issuesTokenCarryingUserId() {
		SessionTokenService service = serviceAt(FIXED_NOW);

		String token = service.issue(USER_ID);

		assertThat(token).isNotBlank();
		assertThat(service.verify(token)).contains(USER_ID);
	}

	@Test
	void verifyReturnsUserId() {
		SessionTokenService service = serviceAt(FIXED_NOW);

		String token = service.issue("user-abc");

		assertThat(service.verify(token)).hasValue("user-abc");
	}

	@Test
	void rejectsTamperedToken() {
		SessionTokenService service = serviceAt(FIXED_NOW);
		String token = service.issue(USER_ID);
		String[] parts = token.split("\\.", -1);
		String tampered = parts[0] + "x." + parts[1];

		assertThat(service.verify(tampered)).isEmpty();
	}

	@Test
	void rejectsTamperedSignature() {
		SessionTokenService service = serviceAt(FIXED_NOW);
		String token = service.issue(USER_ID);
		String[] parts = token.split("\\.", -1);
		String tampered = parts[0] + "." + parts[1] + "x";

		assertThat(service.verify(tampered)).isEmpty();
	}

	@Test
	void rejectsExpiredToken() {
		String token = serviceAt(FIXED_NOW).issue(USER_ID);

		SessionTokenService laterVerifier = serviceAt(FIXED_NOW.plus(Duration.ofHours(12).plusSeconds(1)));

		assertThat(laterVerifier.verify(token)).isEmpty();
	}

	@Test
	void acceptsTokenJustBeforeExpiry() {
		String token = serviceAt(FIXED_NOW).issue(USER_ID);

		SessionTokenService almostExpiredVerifier = serviceAt(FIXED_NOW.plus(Duration.ofHours(11)));

		assertThat(almostExpiredVerifier.verify(token)).contains(USER_ID);
	}

	@Test
	void rejectsNullToken() {
		assertThat(serviceAt(FIXED_NOW).verify(null)).isEmpty();
	}

	@Test
	void rejectsEmptyToken() {
		assertThat(serviceAt(FIXED_NOW).verify("")).isEmpty();
	}

	@Test
	void rejectsMalformedToken() {
		// No '.' separator at all — cannot be split into payload + signature.
		assertThat(serviceAt(FIXED_NOW).verify("noDotHere")).isEmpty();
	}

	@Test
	void rejectsTokenWithMultipleDots() {
		assertThat(serviceAt(FIXED_NOW).verify("a.b.c")).isEmpty();
	}

	@Test
	void rejectsTokenWithBadBase64Payload() {
		assertThat(serviceAt(FIXED_NOW).verify("not-valid-base64!!.YWJj")).isEmpty();
	}

	@Test
	void rejectsTokenWithBadBase64Signature() {
		String validPayload = serviceAt(FIXED_NOW).issue(USER_ID).split("\\.", -1)[0];
		assertThat(serviceAt(FIXED_NOW).verify(validPayload + ".not-valid-base64!!")).isEmpty();
	}

	@Test
	void rejectsTokenWithNonNumericExpiry() throws Exception {
		// Validly signed, well-formed payload whose expiry segment is not a parseable epoch —
		// exercises the expiry-parse failure branch distinctly from the HMAC-mismatch branch.
		String forged = signPayload(USER_ID + ":not-a-number");

		assertThat(serviceAt(FIXED_NOW).verify(forged)).isEmpty();
	}

	@Test
	void rejectsOldIdentitylessToken() throws Exception {
		// A correctly signed token in the OLD format (payload = bare epoch, no "userId:" prefix).
		// Even with a valid signature it must be rejected because it carries no identity.
		long futureEpoch = FIXED_NOW.plus(Duration.ofHours(1)).getEpochSecond();
		String forged = signPayload(Long.toString(futureEpoch));

		assertThat(serviceAt(FIXED_NOW).verify(forged)).isEmpty();
	}

	@Test
	void rejectsTokenWithEmptyUserId() throws Exception {
		// Validly signed payload of the form ":<epoch>" — the userId segment is empty.
		long futureEpoch = FIXED_NOW.plus(Duration.ofHours(1)).getEpochSecond();
		String forged = signPayload(":" + futureEpoch);

		assertThat(serviceAt(FIXED_NOW).verify(forged)).isEmpty();
	}

	@Test
	void differentSecrets_produceIncompatibleTokens() {
		SecurityProperties propsA = new SecurityProperties("pw-a", "secret-a", Duration.ofHours(12));
		SecurityProperties propsB = new SecurityProperties("pw-b", "secret-b", Duration.ofHours(12));
		Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		SessionTokenService serviceA = new SessionTokenService(propsA, clock);
		SessionTokenService serviceB = new SessionTokenService(propsB, clock);

		String token = serviceA.issue(USER_ID);

		assertThat(serviceB.verify(token)).isEmpty();
	}

	/** Signs an arbitrary raw payload with {@link #SECRET}, forging a well-formed token. */
	private static String signPayload(String payload) throws Exception {
		byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
		return encode(bytes) + "." + encode(hmacSha256(bytes));
	}

	private static String encode(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] hmacSha256(byte[] data) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return mac.doFinal(data);
	}
}
