package com.ensemble.security;

/**
 * A session token was cryptographically valid but the account it names no longer exists
 * (deleted out-of-band) — an orphaned token. Distinct from {@link InvalidCredentialsException}
 * (a failed login): the caller presented a valid token and no credentials, so the accurate
 * signal is "re-authenticate", not "invalid email or password". Mapped by
 * {@code com.ensemble.wardrobe.web.ApiExceptionHandler} to a generic
 * {@code 401 ("unauthorized","authentication required")} — the same body the gate filter
 * returns for a missing/invalid token, so the client's re-auth handling is uniform.
 */
public class SessionUserNotFoundException extends RuntimeException {

	public SessionUserNotFoundException() {
		super("session token references a user that no longer exists");
	}
}
