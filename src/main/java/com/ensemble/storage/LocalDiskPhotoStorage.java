package com.ensemble.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.ensemble.config.PhotoProperties;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Stores photos on local disk under a configurable base directory. On save, the
 * image is resized so its longest edge is at most {@value #MAX_EDGE}px (never
 * upscaling a smaller image) and re-encoded as JPEG, keeping stored photos small
 * for a mobile wardrobe. Non-image input is rejected with {@link InvalidImageException}.
 *
 * <p>Keys are resolved inside the base directory; a key that would escape it
 * (path traversal) is rejected — defensive even though keys are derived from
 * server-generated ids.
 */
@Component
public class LocalDiskPhotoStorage implements PhotoStorage {

	static final int MAX_EDGE = 800;

	private final Path baseDir;

	public LocalDiskPhotoStorage(PhotoProperties props) {
		this.baseDir = Path.of(props.dir()).toAbsolutePath().normalize();
		io(() -> Files.createDirectories(baseDir), "create photo directory " + baseDir);
	}

	@Override
	public void save(String key, byte[] imageBytes) {
		byte[] jpeg = toResizedJpeg(imageBytes);
		Path target = resolve(key);
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

	/** Decodes, resizes to ≤{@value #MAX_EDGE}px longest edge (no upscale), re-encodes JPEG. */
	private byte[] toResizedJpeg(byte[] input) {
		BufferedImage image = decode(input);
		if (image == null) {
			throw new InvalidImageException("input is not a decodable image");
		}
		int longestEdge = Math.max(image.getWidth(), image.getHeight());
		double scale = longestEdge > MAX_EDGE ? (double) MAX_EDGE / longestEdge : 1.0;
		int targetW = (int) Math.round(image.getWidth() * scale);
		int targetH = (int) Math.round(image.getHeight() * scale);
		return io(() -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Thumbnails.of(image).size(targetW, targetH).outputFormat("jpg").toOutputStream(out);
			return out.toByteArray();
		}, "encode JPEG");
	}

	private static BufferedImage decode(byte[] input) {
		// ImageIO.read returns null for non-image bytes; a rare I/O failure is
		// routed through io() so both "not an image" paths converge on rejection.
		return io(() -> ImageIO.read(new ByteArrayInputStream(input)), "decode image");
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
