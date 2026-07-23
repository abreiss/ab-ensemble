package com.ensemble.user.web;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.security.InvalidPasscodeException;
import com.ensemble.security.SessionTokenService;
import com.ensemble.security.SignupPasscodeVerifier;
import com.ensemble.security.dto.AuthResponse;
import com.ensemble.user.DuplicateEmailException;
import com.ensemble.user.PasswordHasher;
import com.ensemble.user.User;
import com.ensemble.user.UserRepository;

import jakarta.validation.Valid;

/**
 * {@code POST /api/accounts} — invite-only sign-up (issue #14). Validates the request
 * ({@link SignupRequest}: {@code @Email}, password 8–72 UTF-8 bytes, non-blank passcode) on bind,
 * verifies the shared signup passcode with a constant-time compare
 * ({@link SignupPasscodeVerifier}), hashes the password (bcrypt), and atomically creates the
 * account (a duplicate email fails at the datastore, surfaced as {@link DuplicateEmailException}
 * → {@code 409}). On success it <strong>auto-logs the user in</strong> by minting a session token
 * for the new {@code userId} and returning {@code 201 { token }}.
 *
 * <p>Order matters for the invite gate: the passcode is checked <em>before</em> any hashing or
 * persistence, so a wrong or blank passcode throws {@link InvalidPasscodeException} ({@code 401},
 * generic) and <strong>creates no user</strong> — a stranger who finds the URL cannot register.
 * The endpoint is reachable token-free (see {@code SessionAuthFilter}); everything hostile it can
 * receive is bounded by validation before it costs any crypto or a datastore write.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	private final UserRepository users;
	private final PasswordHasher passwordHasher;
	private final SignupPasscodeVerifier signupPasscodeVerifier;
	private final SessionTokenService tokenService;
	private final Clock clock;

	public AccountController(UserRepository users, PasswordHasher passwordHasher,
			SignupPasscodeVerifier signupPasscodeVerifier, SessionTokenService tokenService, Clock clock) {
		this.users = users;
		this.passwordHasher = passwordHasher;
		this.signupPasscodeVerifier = signupPasscodeVerifier;
		this.tokenService = tokenService;
		this.clock = clock;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
		if (!signupPasscodeVerifier.matches(request.passcode())) {
			throw new InvalidPasscodeException();
		}
		User user = new User();
		user.setUserId(UUID.randomUUID().toString());
		user.setEmail(request.email());
		user.setPasswordHash(passwordHasher.hash(request.password()));
		user.setCreatedAt(clock.instant());
		users.create(user);
		return new AuthResponse(tokenService.issue(user.getUserId()));
	}
}
