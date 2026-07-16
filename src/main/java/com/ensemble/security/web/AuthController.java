package com.ensemble.security.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.security.InvalidPasscodeException;
import com.ensemble.security.SecurityProperties;
import com.ensemble.security.SessionTokenService;
import com.ensemble.security.dto.AuthRequest;
import com.ensemble.security.dto.AuthResponse;

/**
 * {@code POST /api/auth} — the single unauthenticated entry point that trades a correct
 * passcode for a signed session token. A blank or mismatched passcode throws
 * {@link InvalidPasscodeException}, mapped by the shared
 * {@code com.ensemble.wardrobe.web.ApiExceptionHandler} to a sanitized {@code 401} with
 * no token, so a wrong guess never reveals whether the gate is even configured.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final SecurityProperties properties;
	private final SessionTokenService tokenService;

	public AuthController(SecurityProperties properties, SessionTokenService tokenService) {
		this.properties = properties;
		this.tokenService = tokenService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public AuthResponse authenticate(@RequestBody AuthRequest request) {
		if (!matchesConfiguredPasscode(request.passcode())) {
			throw new InvalidPasscodeException();
		}
		return new AuthResponse(tokenService.issue());
	}

	/**
	 * Constant-time comparison against the configured passcode. Both candidates are
	 * hashed first so the comparison is fixed-length regardless of input length (a blank
	 * submission is rejected outright, so a blank/unconfigured server passcode can never
	 * be "matched" by a blank guess).
	 */
	private boolean matchesConfiguredPasscode(String candidate) {
		if (candidate == null || candidate.isBlank()) {
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
