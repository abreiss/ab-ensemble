package com.ensemble.user;

/**
 * Thrown when an account cannot be created because its (normalized) username is
 * already registered. Surfaced by {@link UserRepository#create} when the atomic
 * {@code attribute_not_exists(username)} conditional put fails, and mapped to a
 * {@code 409 Conflict} by the API exception handler.
 */
public class DuplicateUsernameException extends RuntimeException {

	public DuplicateUsernameException(String message) {
		super(message);
	}
}
