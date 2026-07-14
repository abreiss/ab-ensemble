package com.ensemble.storage;

/** Thrown when {@link PhotoStorage#load} is asked for a key with no stored photo. */
public class PhotoNotFoundException extends RuntimeException {

	public PhotoNotFoundException(String key) {
		super("photo not found: " + key);
	}
}
