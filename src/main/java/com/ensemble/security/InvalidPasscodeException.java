package com.ensemble.security;

/** A missing, blank, or mismatched passcode was submitted to {@code POST /api/auth}. */
public class InvalidPasscodeException extends RuntimeException {

	public InvalidPasscodeException() {
		super("invalid passcode");
	}
}
