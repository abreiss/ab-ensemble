package com.ensemble.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/accounts} (sign up, issue #14). Bean-validated on bind
 * ({@code @Valid}): a blank/invalid email, a password shorter than 8 characters or longer than 72
 * UTF-8 bytes (bcrypt's input limit — see {@link MaxUtf8Bytes}), or a blank signup passcode all
 * fail binding and are rejected with the shared sanitized {@code 400} before any crypto, passcode
 * check, or persistence work.
 *
 * @param email the account email; normalized (trimmed + lowercased) when the {@code User} is built.
 * @param password the raw password; hashed with bcrypt before persistence, never stored or logged.
 * @param passcode the shared signup passcode gating invite-only registration.
 */
public record SignupRequest(
	@NotBlank @Email String email,
	@NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password,
	@NotBlank String passcode) {
}
