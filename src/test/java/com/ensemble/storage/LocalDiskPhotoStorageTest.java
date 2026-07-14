package com.ensemble.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ensemble.config.PhotoProperties;

/**
 * Unit tests for the disk-storage concerns: save→load round-trip (delegating the
 * ≤800px-JPEG resize to {@link ImageProcessor}), delete, path-traversal rejection,
 * and checked-IO wrapping. The image decode/resize/pixel-cap branch logic lives in
 * {@link ImageProcessorTest}.
 */
class LocalDiskPhotoStorageTest {

	private static final long DEFAULT_MAX_PIXELS = 50_000_000L;

	@TempDir
	Path tempDir;

	private LocalDiskPhotoStorage storage;

	@BeforeEach
	void setUp() {
		PhotoProperties props = new PhotoProperties(tempDir.toString(), DEFAULT_MAX_PIXELS);
		storage = new LocalDiskPhotoStorage(props, new ImageProcessor(props));
	}

	private static byte[] pngOf(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	@Test
	void save_resizesViaImageProcessor_andStoresJpeg() throws IOException {
		// Delegation proof: a >800px PNG comes back as a ≤800px JPEG on load.
		storage.save("big.jpg", pngOf(1200, 600));

		byte[] stored = storage.load("big.jpg");
		BufferedImage out = decode(stored);
		assertThat(out.getWidth()).isEqualTo(800);
		assertThat(out.getHeight()).isEqualTo(400);
		assertThat(stored[0] & 0xFF).isEqualTo(0xFF);
		assertThat(stored[1] & 0xFF).isEqualTo(0xD8);
	}

	@Test
	void save_nonImageBytes_propagatesInvalidImageException() {
		// Storage delegates validation to ImageProcessor; a non-image is rejected on save.
		byte[] notAnImage = "this is definitely not an image".getBytes(StandardCharsets.UTF_8);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> storage.save("bad.jpg", notAnImage));
	}

	@Test
	void load_unknownKey_throwsPhotoNotFound() {
		assertThatExceptionOfType(PhotoNotFoundException.class)
			.isThrownBy(() -> storage.load("missing.jpg"));
	}

	@Test
	void delete_removesStoredPhoto() throws IOException {
		storage.save("temp.jpg", pngOf(100, 100));

		storage.delete("temp.jpg");

		assertThatExceptionOfType(PhotoNotFoundException.class)
			.isThrownBy(() -> storage.load("temp.jpg"));
	}

	@Test
	void delete_unknownKey_isNoOp() {
		storage.delete("never-existed.jpg"); // must not throw
	}

	@Test
	void resolve_pathTraversalKey_isRejected() {
		assertThatThrownBy(() -> storage.load("../escape.jpg"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void load_whenKeyIsADirectory_wrapsIoErrorAsUnchecked() throws IOException {
		// A key that resolves to a directory triggers a real I/O failure on read,
		// exercising the checked-IO → unchecked wrapper path.
		java.nio.file.Files.createDirectory(tempDir.resolve("adir"));

		assertThatExceptionOfType(java.io.UncheckedIOException.class)
			.isThrownBy(() -> storage.load("adir"));
	}
}
