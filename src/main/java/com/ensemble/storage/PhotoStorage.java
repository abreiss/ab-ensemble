package com.ensemble.storage;

/**
 * Stores garment photos behind a swappable seam. The dev implementation writes
 * to local disk; an S3 implementation arrives at deploy (issue #9). Application
 * code depends only on this interface, never on a concrete storage type, so the
 * disk→S3 swap is configuration rather than a rewrite.
 *
 * <p>Implementations compress/resize on {@link #save} (see the ≤800px JPEG rule
 * in {@code LocalDiskPhotoStorage}). The {@code key} is chosen by the caller and
 * is derived from the server-generated {@code itemId}, so it is safe from client
 * tampering.
 */
public interface PhotoStorage {

	/**
	 * Stores an image under {@code key}, compressing/resizing it first.
	 *
	 * @throws InvalidImageException if {@code imageBytes} is not a decodable image
	 */
	void save(String key, byte[] imageBytes);

	/**
	 * Loads the stored (already-compressed) image bytes.
	 *
	 * @throws PhotoNotFoundException if no photo exists for {@code key}
	 */
	byte[] load(String key);

	/** Removes the photo for {@code key}; a no-op if it does not exist. */
	void delete(String key);
}
