package com.ensemble.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/accounts} (sign up, issue #14). Bean-validated on bind
 * ({@code @Valid}): a blank/malformed username (length outside 3–30, an illegal character, or a
 * leading/trailing separator), a password shorter than 8 characters or longer than 72 UTF-8 bytes
 * (bcrypt's input limit — see {@link MaxUtf8Bytes}), or a blank signup passcode all fail binding
 * and are rejected with the shared sanitized {@code 400} before any crypto, passcode check, or
 * persistence work.
 *
 * @param username the account username: 3–30 chars, alphanumeric with interior {@code . _ -}
 *                 separators (no leading/trailing separator); normalized (trimmed + lowercased)
 *                 when the {@code User} is built.
 * @param password the raw password; hashed with bcrypt before persistence, never stored or logged.
 * @param passcode the shared signup passcode gating invite-only registration.
 */
public record SignupRequest(
	@NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$") String username,
	@NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password,
	@NotBlank String passcode) {
}
