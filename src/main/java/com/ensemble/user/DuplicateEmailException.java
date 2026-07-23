package com.ensemble.user;

/**
 * Thrown when an account cannot be created because its (normalized) email is
 * already registered. Surfaced by {@link UserRepository#create} when the atomic
 * {@code attribute_not_exists(email)} conditional put fails, and mapped to a
 * {@code 409 Conflict} by the API exception handler.
 */
public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String message) {
		super(message);
	}
}
