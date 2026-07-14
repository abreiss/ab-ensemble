package com.ensemble.storage;

/** Thrown when bytes offered to {@link PhotoStorage#save} are not a decodable image. */
public class InvalidImageException extends RuntimeException {

	public InvalidImageException(String message) {
		super(message);
	}
}
