package com.ensemble.wardrobe;

/** Thrown when an operation targets an {@code itemId} that does not exist. */
public class ItemNotFoundException extends RuntimeException {

	public ItemNotFoundException(String itemId) {
		super("item not found: " + itemId);
	}
}
