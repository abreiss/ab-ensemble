package com.ensemble.security;

/**
 * Login failed: either the email is not registered or the password is wrong. The <em>same</em>
 * exception is thrown for both so the response never reveals which — preventing user
 * enumeration. Mapped by {@code com.ensemble.wardrobe.web.ApiExceptionHandler} to a generic
 * {@code 401 ("unauthorized","invalid email or password")}.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("invalid email or password");
	}
}
