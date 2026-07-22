package com.ensemble.outfit;

/**
 * Thrown by the save-time grounding guard when a save is rejected as a whole:
 * an empty {@code itemIds} list, a {@code source} outside {@code {ai, manual}}, or
 * any {@code itemId} that is not a known wardrobe item. The message is user-safe
 * (it echoes only client-supplied values, never internals) so it can be surfaced
 * to the caller as a {@code 400 bad_request}.
 */
public class InvalidOutfitException extends RuntimeException {

	public InvalidOutfitException(String message) {
		super(message);
	}
}
