package com.ensemble.outfit;

/** Thrown when an operation targets an {@code outfitId} that does not exist. */
public class OutfitNotFoundException extends RuntimeException {

	public OutfitNotFoundException(String outfitId) {
		super("outfit not found: " + outfitId);
	}
}
