package com.ensemble.usage;

/** Signals that the global daily call cap has been exceeded; mapped to {@code 429}. */
public class DailyCapExceededException extends RuntimeException {

	public DailyCapExceededException(String message) {
		super(message);
	}
}
