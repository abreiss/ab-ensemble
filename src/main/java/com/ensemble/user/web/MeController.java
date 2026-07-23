package com.ensemble.user.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.security.InvalidCredentialsException;
import com.ensemble.security.web.CurrentUserId;
import com.ensemble.user.UserRepository;

/**
 * {@code GET /api/me} — returns the authenticated caller's identity (issue #14): the opaque
 * {@code userId} the session token carries plus the account email. The demoable proof that
 * {@code SessionAuthFilter} resolves a concrete principal.
 *
 * <p>The token carries only the opaque {@code userId} (no email/PII — see the spec's
 * "Security"), so the email is looked up via {@link UserRepository#findByUserId} (a
 * demo-scale scan; the table is email-keyed with no {@code userId} GSI). A valid session
 * whose account no longer exists (deleted out-of-band) resolves to nothing and is treated as
 * unauthenticated — the caller re-authenticates.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

	/** The authenticated caller's identity. Opaque {@code userId} + account email; no other PII. */
	public record MeResponse(String userId, String email) {
	}

	private final UserRepository users;

	public MeController(UserRepository users) {
		this.users = users;
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public MeResponse me(@CurrentUserId String userId) {
		return users.findByUserId(userId)
			.map(user -> new MeResponse(user.getUserId(), user.getEmail()))
			.orElseThrow(InvalidCredentialsException::new);
	}
}
