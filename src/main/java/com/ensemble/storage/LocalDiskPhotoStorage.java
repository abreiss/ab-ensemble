package com.ensemble.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import com.ensemble.config.PhotoProperties;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Stores photos on local disk under a configurable base directory. On save, the
 * image is resized so its longest edge is at most {@value #MAX_EDGE}px (never
 * upscaling a smaller image) and re-encoded as JPEG, keeping stored photos small
 * for a mobile wardrobe.
 *
 * <p>Input is validated before the full raster is materialised: bytes with no
 * image reader, a corrupt/truncated body, or a declared pixel count above the
 * configured cap are all rejected with {@link InvalidImageException} — the last
 * guards against decompression-bomb images that are small compressed but huge in
 * memory.
 *
 * <p>Keys are resolved inside the base directory; a key that would escape it
 * (path traversal) is rejected — defensive even though keys are derived from
 * server-generated ids.
 */
@Component
public class LocalDiskPhotoStorage implements PhotoStorage {

	static final int MAX_EDGE = 800;

	private final Path baseDir;
	private final long maxUploadPixels;

	public LocalDiskPhotoStorage(PhotoProperties props) {
		this.baseDir = Path.of(props.dir()).toAbsolutePath().normalize();
		this.maxUploadPixels = props.maxUploadPixels();
		io(() -> Files.createDirectories(baseDir), "create photo directory " + baseDir);
	}

	@Override
	public void save(String key, byte[] imageBytes) {
		Path target = resolve(key); // reject a bad key before any decode work
		byte[] jpeg = toResizedJpeg(imageBytes);
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
		int longestEdge = Math.max(image.getWidth(), image.getHeight());
		double scale = longestEdge > MAX_EDGE ? (double) MAX_EDGE / longestEdge : 1.0;
		// Clamp to at least 1px: an extreme aspect ratio can round the short edge to 0,
		// which the JPEG encoder rejects.
		int targetW = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int targetH = Math.max(1, (int) Math.round(image.getHeight() * scale));
		return io(() -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Thumbnails.of(image).size(targetW, targetH).outputFormat("jpg").toOutputStream(out);
			return out.toByteArray();
		}, "encode JPEG");
	}

	/**
	 * Validates and decodes the input with a single image reader. Rejects (as
	 * {@link InvalidImageException}) any bytes with no reader, a declared pixel count
	 * over the cap, or a body that fails to decode. The pixel count is read from the
	 * header before {@code read} materialises the raster, so a decompression-bomb
	 * image never allocates its full bitmap.
	 */
	private BufferedImage decode(byte[] input) {
		try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				throw new InvalidImageException("input is not a decodable image");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis);
				long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
				if (pixels > maxUploadPixels) {
					throw new InvalidImageException(
						"image of " + pixels + "px exceeds the " + maxUploadPixels + "px limit");
				}
				return readRaster(reader);
			} finally {
				reader.dispose();
			}
		} catch (InvalidImageException e) {
			throw e; // header / pixel-cap rejections carry their own message
		} catch (IOException | RuntimeException e) {
			// A truncated/corrupt image fails mid-parse — some readers throw unchecked on
			// crafted input. Either way it is invalid input, not a server fault.
			throw new InvalidImageException("input is not a decodable image");
		}
	}

	/** Reads the full raster. Isolated as a seam so decode failures can be exercised in tests. */
	BufferedImage readRaster(ImageReader reader) throws IOException {
		return reader.read(0);
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
