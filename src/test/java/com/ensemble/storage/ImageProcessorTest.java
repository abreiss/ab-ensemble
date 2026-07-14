package com.ensemble.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.junit.jupiter.api.Test;

import com.ensemble.config.PhotoProperties;

/**
 * Unit tests for the shared ≤800px-JPEG image guard — the critical-branch logic
 * (downscale-if-larger, no-upscale, clamp-to-1, decompression-bomb pixel cap,
 * reject-non-image) reused by both storage and vision tagging.
 */
class ImageProcessorTest {

	private static final long DEFAULT_MAX_PIXELS = 50_000_000L;

	private final ImageProcessor processor =
		new ImageProcessor(new PhotoProperties("unused", DEFAULT_MAX_PIXELS));

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
	void largeImage_isDownscaledToMax800AndEncodedJpeg() throws IOException {
		byte[] out = processor.toResizedJpeg(pngOf(1200, 600));

		BufferedImage image = decode(out);
		assertThat(image.getWidth()).isEqualTo(800);
		assertThat(image.getHeight()).isEqualTo(400);
		// JPEG SOI magic bytes 0xFF 0xD8.
		assertThat(out[0] & 0xFF).isEqualTo(0xFF);
		assertThat(out[1] & 0xFF).isEqualTo(0xD8);
	}

	@Test
	void smallImage_isNotUpscaled() throws IOException {
		BufferedImage image = decode(processor.toResizedJpeg(pngOf(300, 200)));
		assertThat(image.getWidth()).isEqualTo(300);
		assertThat(image.getHeight()).isEqualTo(200);
	}

	@Test
	void imageExactlyAtMax_isKept() throws IOException {
		BufferedImage image = decode(processor.toResizedJpeg(pngOf(800, 500)));
		assertThat(image.getWidth()).isEqualTo(800);
		assertThat(image.getHeight()).isEqualTo(500);
	}

	@Test
	void nonImageBytes_throwsInvalidImageException() {
		byte[] notAnImage = "this is definitely not an image".getBytes(StandardCharsets.UTF_8);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> processor.toResizedJpeg(notAnImage));
	}

	@Test
	void truncatedImage_throwsInvalidImageException() throws IOException {
		byte[] truncated = Arrays.copyOf(jpegOf(1000, 1000), 100);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> processor.toResizedJpeg(truncated));
	}

	@Test
	void imageExceedingPixelCap_throwsInvalidImageException() throws IOException {
		ImageProcessor capped = new ImageProcessor(new PhotoProperties("unused", 10_000L));

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> capped.toResizedJpeg(pngOf(200, 200)));
	}

	@Test
	void whenReaderThrowsUnchecked_throwsInvalidImageException() throws IOException {
		ImageProcessor faulty = new ImageProcessor(new PhotoProperties("unused", DEFAULT_MAX_PIXELS)) {
			@Override
			BufferedImage readRaster(ImageReader reader) {
				throw new ArrayIndexOutOfBoundsException("corrupt scan data");
			}
		};

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> faulty.toResizedJpeg(pngOf(100, 100)));
	}

	@Test
	void extremeAspectRatio_downscalesWithoutZeroDimension() throws IOException {
		// 1×1601 scales width to round(0.4997)=0; must clamp to ≥1 so the encoder never
		// gets a zero dimension.
		BufferedImage image = decode(processor.toResizedJpeg(pngOf(1, 1601)));
		assertThat(image.getWidth()).isEqualTo(1);
		assertThat(image.getHeight()).isEqualTo(800);
	}
}
