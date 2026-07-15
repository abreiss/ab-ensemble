package com.ensemble.stylist;

/**
 * Thrown when the stylist cannot return a grounded outfit: an upstream failure
 * (Claude error/timeout) or an ungroundable result (zero valid ids remain after
 * the one retry, including a malformed/empty pick). {@code ApiExceptionHandler}
 * maps it to a graceful error response so the client shows a friendly message
 * rather than a stack trace — and an unvalidated id is never rendered.
 *
 * <p>Distinct from the empty-wardrobe case, which is a normal {@code 200} with an
 * empty outfit, not an error.
 */
public class StylistUnavailableException extends RuntimeException {

	public StylistUnavailableException(String message) {
		super(message);
	}

	public StylistUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
