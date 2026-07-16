package com.ensemble.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

	private static final Instant FIXED_NOW = Instant.parse("2026-07-16T12:00:00Z");

	private SessionTokenService service(Clock clock) {
		SecurityProperties props = new SecurityProperties("demo-pw", "demo-secret", Duration.ofHours(12));
		return new SessionTokenService(props, clock);
	}

	private SessionTokenService serviceAt(Instant now) {
		return service(Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void issuesTokenThatVerifies() {
		SessionTokenService service = serviceAt(FIXED_NOW);

		String token = service.issue();

		assertThat(token).isNotBlank();
		assertThat(service.verify(token)).isTrue();
	}

	@Test
	void rejectsTamperedToken() {
		SessionTokenService service = serviceAt(FIXED_NOW);
		String token = service.issue();
		String[] parts = token.split("\\.", -1);
		String tampered = parts[0] + "x." + parts[1];

		assertThat(service.verify(tampered)).isFalse();
	}

	@Test
	void rejectsTamperedSignature() {
		SessionTokenService service = serviceAt(FIXED_NOW);
		String token = service.issue();
		String[] parts = token.split("\\.", -1);
		String tampered = parts[0] + "." + parts[1] + "x";

		assertThat(service.verify(tampered)).isFalse();
	}

	@Test
	void rejectsExpiredToken() {
		SessionTokenService issuer = serviceAt(FIXED_NOW);
		String token = issuer.issue();

		SessionTokenService laterVerifier = serviceAt(FIXED_NOW.plus(Duration.ofHours(12).plusSeconds(1)));

		assertThat(laterVerifier.verify(token)).isFalse();
	}

	@Test
	void acceptsTokenJustBeforeExpiry() {
		SessionTokenService issuer = serviceAt(FIXED_NOW);
		String token = issuer.issue();

		SessionTokenService almostExpiredVerifier = serviceAt(FIXED_NOW.plus(Duration.ofHours(11)));

		assertThat(almostExpiredVerifier.verify(token)).isTrue();
	}

	@Test
	void rejectsNullToken() {
		assertThat(serviceAt(FIXED_NOW).verify(null)).isFalse();
	}

	@Test
	void rejectsEmptyToken() {
		assertThat(serviceAt(FIXED_NOW).verify("")).isFalse();
	}

	@Test
	void rejectsTokenWithNoDot() {
		assertThat(serviceAt(FIXED_NOW).verify("noDotHere")).isFalse();
	}

	@Test
	void rejectsTokenWithMultipleDots() {
		assertThat(serviceAt(FIXED_NOW).verify("a.b.c")).isFalse();
	}

	@Test
	void rejectsTokenWithBadBase64Payload() {
		assertThat(serviceAt(FIXED_NOW).verify("not-valid-base64!!.YWJj")).isFalse();
	}

	@Test
	void rejectsTokenWithBadBase64Signature() {
		String validPayload = serviceAt(FIXED_NOW).issue().split("\\.", -1)[0];
		assertThat(serviceAt(FIXED_NOW).verify(validPayload + ".not-valid-base64!!")).isFalse();
	}

	@Test
	void rejectsTokenWithNonNumericPayload() throws Exception {
		// A validly signed token whose payload is not a parseable epoch — exercises the
		// payload-parsing failure branch of verify() distinctly from the HMAC-mismatch branch.
		byte[] payloadBytes = "not-a-number".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String forged = encode(payloadBytes) + "." + encode(hmacSha256(payloadBytes, "demo-secret"));

		assertThat(serviceAt(FIXED_NOW).verify(forged)).isFalse();
	}

	private static String encode(byte[] bytes) {
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static byte[] hmacSha256(byte[] data, String secret) throws Exception {
		javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
		mac.init(new javax.crypto.spec.SecretKeySpec(
			secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
		return mac.doFinal(data);
	}

	@Test
	void differentSecrets_produceIncompatibleTokens() {
		SecurityProperties propsA = new SecurityProperties("pw-a", "secret-a", Duration.ofHours(12));
		SecurityProperties propsB = new SecurityProperties("pw-b", "secret-b", Duration.ofHours(12));
		Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		SessionTokenService serviceA = new SessionTokenService(propsA, clock);
		SessionTokenService serviceB = new SessionTokenService(propsB, clock);

		String token = serviceA.issue();

		assertThat(serviceB.verify(token)).isFalse();
	}
}
