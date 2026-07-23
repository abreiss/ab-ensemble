package com.ensemble.security;

/** A missing, blank, or mismatched signup/invite passcode was submitted to {@code POST /api/accounts}. */
public class InvalidPasscodeException extends RuntimeException {

	public InvalidPasscodeException() {
		super("invalid passcode");
	}
}
