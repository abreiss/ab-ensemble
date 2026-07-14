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
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ensemble.config.PhotoProperties;

/**
 * Unit tests for the ≤800px-JPEG storage rules — the task's critical-branch
 * logic: downscale-if-larger, no-upscale, and reject-non-image.
 */
class LocalDiskPhotoStorageTest {

	private static final long DEFAULT_MAX_PIXELS = 50_000_000L;

	@TempDir
	Path tempDir;

	private LocalDiskPhotoStorage storage;

	@BeforeEach
	void setUp() {
		storage = new LocalDiskPhotoStorage(new PhotoProperties(tempDir.toString(), DEFAULT_MAX_PIXELS));
	}

	private static byte[] pngOf(int width, int height) throws IOException {
		return encode(width, height, "png");
	}

	private static byte[] jpegOf(int width, int height) throws IOException {
		return encode(width, height, "jpg");
	}

	private static byte[] encode(int width, int height, String format) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] bytes) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	@Test
	void save_largeImage_isDownscaledToMax800AndStoredAsJpeg() throws IOException {
		storage.save("big.jpg", pngOf(1200, 600));

		byte[] stored = storage.load("big.jpg");
		BufferedImage out = decode(stored);
		assertThat(Math.max(out.getWidth(), out.getHeight())).isEqualTo(800);
		assertThat(out.getWidth()).isEqualTo(800);
		assertThat(out.getHeight()).isEqualTo(400);
		// JPEG SOI magic bytes 0xFF 0xD8.
		assertThat(stored[0] & 0xFF).isEqualTo(0xFF);
		assertThat(stored[1] & 0xFF).isEqualTo(0xD8);
	}

	@Test
	void save_smallImage_isNotUpscaled() throws IOException {
		storage.save("small.jpg", pngOf(300, 200));

		BufferedImage out = decode(storage.load("small.jpg"));
		assertThat(out.getWidth()).isEqualTo(300);
		assertThat(out.getHeight()).isEqualTo(200);
	}

	@Test
	void save_imageExactlyAtMax_isKept() throws IOException {
		storage.save("edge.jpg", pngOf(800, 500));

		BufferedImage out = decode(storage.load("edge.jpg"));
		assertThat(out.getWidth()).isEqualTo(800);
		assertThat(out.getHeight()).isEqualTo(500);
	}

	@Test
	void save_nonImageBytes_throwsInvalidImageException() {
		byte[] notAnImage = "this is definitely not an image".getBytes(StandardCharsets.UTF_8);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> storage.save("bad.jpg", notAnImage));
	}

	@Test
	void save_truncatedImage_throwsInvalidImageException() throws IOException {
		// A valid JPEG header followed by a cut-off body decodes to an IOException
		// mid-parse; that must surface as a 400-mapped InvalidImageException, not a
		// 500-mapped UncheckedIOException.
		byte[] jpeg = jpegOf(1000, 1000);
		byte[] truncated = Arrays.copyOf(jpeg, 100);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> storage.save("truncated.jpg", truncated));
	}

	@Test
	void save_imageExceedingPixelCap_throwsInvalidImageException() throws IOException {
		// A tiny pixel cap stands in for the decompression-bomb guard: an image whose
		// pixel count exceeds the cap is rejected from its cheap declared dimensions,
		// before the full raster is decoded into memory.
		LocalDiskPhotoStorage capped =
			new LocalDiskPhotoStorage(new PhotoProperties(tempDir.toString(), 10_000L));

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> capped.save("bomb.jpg", pngOf(200, 200)));
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
