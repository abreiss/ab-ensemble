package com.ensemble.security.web;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.security.InvalidCredentialsException;
import com.ensemble.security.SessionTokenService;
import com.ensemble.security.dto.AuthRequest;
import com.ensemble.security.dto.AuthResponse;
import com.ensemble.user.PasswordHasher;
import com.ensemble.user.User;
import com.ensemble.user.UserRepository;

import jakarta.validation.Valid;

/**
 * {@code POST /api/auth} — username/password login (issue #14). On a valid username + password it
 * returns {@code 200} with a signed session token carrying that user's {@code userId}
 * (see {@link SessionTokenService}). An unknown username <strong>or</strong> a wrong password
 * yields the <em>same</em> generic {@link InvalidCredentialsException} ({@code 401}), so the
 * response never reveals whether a username is registered.
 *
 * <p>To close the user-enumeration <em>timing</em> side-channel, login performs a bcrypt
 * comparison even when the username is absent — against {@link #dummyHash}, a valid
 * work-factor-matching hash computed once at construction — so the unknown-username path costs
 * the same as the wrong-password path. Mirrors Spring Security's
 * {@code DaoAuthenticationProvider} idiom.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	/**
	 * Throwaway input hashed once at construction purely to obtain a valid bcrypt hash of the
	 * correct work factor for timing equalization. Never a real credential and never matched
	 * intentionally.
	 */
	private static final String TIMING_EQUALIZER_INPUT = "ensemble-login-timing-equalizer";

	private final UserRepository users;
	private final PasswordHasher passwordHasher;
	private final SessionTokenService tokenService;
	private final String dummyHash;

	public AuthController(UserRepository users, PasswordHasher passwordHasher, SessionTokenService tokenService) {
		this.users = users;
		this.passwordHasher = passwordHasher;
		this.tokenService = tokenService;
		this.dummyHash = passwordHasher.hash(TIMING_EQUALIZER_INPUT);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public AuthResponse authenticate(@Valid @RequestBody AuthRequest request) {
		Optional<User> user = users.findByUsername(request.username());
		if (user.isPresent() && passwordHasher.matches(request.password(), user.get().getPasswordHash())) {
			return new AuthResponse(tokenService.issue(user.get().getUserId()));
		}
		if (user.isEmpty()) {
			// Timing equalization: run one bcrypt comparison even when the username is unknown,
			// so response time does not leak whether the username is registered.
			passwordHasher.matches(request.password(), dummyHash);
		}
		throw new InvalidCredentialsException();
	}
}
