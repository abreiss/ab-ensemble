package com.ensemble.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.ensemble.config.PhotoProperties;

/**
 * Stores photos on local disk under a configurable base directory. On save, the
 * bytes are validated and resized to a small JPEG by the shared
 * {@link ImageProcessor} (≤800px longest edge, decompression-bomb pixel cap),
 * then written.
 *
 * <p>Keys are resolved inside the base directory; a key that would escape it
 * (path traversal) is rejected — defensive even though keys are derived from
 * server-generated ids.
 */
@Component
public class LocalDiskPhotoStorage implements PhotoStorage {

	private final Path baseDir;
	private final ImageProcessor imageProcessor;

	public LocalDiskPhotoStorage(PhotoProperties props, ImageProcessor imageProcessor) {
		this.baseDir = Path.of(props.dir()).toAbsolutePath().normalize();
		this.imageProcessor = imageProcessor;
		io(() -> Files.createDirectories(baseDir), "create photo directory " + baseDir);
	}

	@Override
	public void save(String key, byte[] imageBytes) {
		Path target = resolve(key); // reject a bad key before any decode work
		byte[] jpeg = imageProcessor.toResizedJpeg(imageBytes);
		io(() -> Files.write(target, jpeg), "write photo " + key);
	}

	@Override
	public byte[] load(String key) {
		Path path = resolve(key);
		if (!Files.exists(path)) {
			throw new PhotoNotFoundException(key);
		}
		return io(() -> Files.readAllBytes(path), "read photo " + key);
	}

	@Override
	public void delete(String key) {
		Path path = resolve(key);
		io(() -> Files.deleteIfExists(path), "delete photo " + key);
	}

	/** Resolves a key inside the base dir, rejecting any path-traversal attempt. */
	private Path resolve(String key) {
		Path resolved = baseDir.resolve(key).normalize();
		if (!resolved.startsWith(baseDir)) {
			throw new IllegalArgumentException("invalid photo key: " + key);
		}
		return resolved;
	}

	/** Runs a checked-IO operation, rethrowing failures as unchecked. */
	private static <T> T io(IoSupplier<T> op, String action) {
		try {
			return op.get();
		} catch (IOException e) {
			throw new UncheckedIOException("failed to " + action, e);
		}
	}

	@FunctionalInterface
	private interface IoSupplier<T> {
		T get() throws IOException;
	}
}
