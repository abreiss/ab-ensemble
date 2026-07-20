package com.ensemble.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.config.PhotoProperties;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Unit tests for the S3 storage concerns: save/load/delete build the correct
 * S3 requests against a mocked {@link S3Client} (no live network), and
 * compression is delegated to {@link ImageProcessor} exactly as
 * {@code LocalDiskPhotoStorage} does. The image decode/resize/pixel-cap branch
 * logic itself lives in {@link ImageProcessorTest}.
 */
@ExtendWith(MockitoExtension.class)
class S3PhotoStorageTest {

	private static final long DEFAULT_MAX_PIXELS = 50_000_000L;
	private static final String BUCKET = "abreiss-ensemble-photos";

	@Mock
	S3Client s3Client;

	private S3PhotoStorage storage;

	@BeforeEach
	void setUp() {
		PhotoProperties props =
			new PhotoProperties("unused", DEFAULT_MAX_PIXELS, "s3", new PhotoProperties.S3(BUCKET));
		storage = new S3PhotoStorage(s3Client, props, new ImageProcessor(props));
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

	private static byte[] readAll(RequestBody body) throws IOException {
		try (var stream = body.contentStreamProvider().newStream()) {
			return stream.readAllBytes();
		}
	}

	@Test
	void save_compressesViaImageProcessor_andPutsJpegToBucket() throws IOException {
		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
		when(s3Client.putObject(requestCaptor.capture(), bodyCaptor.capture()))
			.thenReturn(PutObjectResponse.builder().build());

		storage.save("item.jpg", pngOf(1200, 600));

		PutObjectRequest request = requestCaptor.getValue();
		assertThat(request.bucket()).isEqualTo(BUCKET);
		assertThat(request.key()).isEqualTo("item.jpg");
		assertThat(request.contentType()).isEqualTo("image/jpeg");
		// Compression proof: the >800px PNG passed in comes out as a <=800px JPEG body,
		// exactly what LocalDiskPhotoStorage would have written.
		byte[] body = readAll(bodyCaptor.getValue());
		BufferedImage compressed = ImageIO.read(new ByteArrayInputStream(body));
		assertThat(compressed.getWidth()).isEqualTo(800);
		assertThat(compressed.getHeight()).isEqualTo(400);
		assertThat(body[0] & 0xFF).isEqualTo(0xFF);
		assertThat(body[1] & 0xFF).isEqualTo(0xD8);
	}

	@Test
	void save_nonImageBytes_propagatesInvalidImageException() {
		byte[] notAnImage = "this is definitely not an image".getBytes(StandardCharsets.UTF_8);

		assertThatExceptionOfType(InvalidImageException.class)
			.isThrownBy(() -> storage.save("bad.jpg", notAnImage));
	}

	@Test
	void load_returnsStoredBytesFromBucket() {
		ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
		byte[] expected = {1, 2, 3};
		ResponseBytes<GetObjectResponse> response =
			ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected);
		when(s3Client.getObjectAsBytes(requestCaptor.capture())).thenReturn(response);

		byte[] actual = storage.load("item.jpg");

		assertThat(actual).isEqualTo(expected);
		assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
		assertThat(requestCaptor.getValue().key()).isEqualTo("item.jpg");
	}

	@Test
	void load_missingKey_throwsPhotoNotFound() {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("no such key").build());

		assertThatExceptionOfType(PhotoNotFoundException.class)
			.isThrownBy(() -> storage.load("missing.jpg"));
	}

	@Test
	void delete_removesObjectFromBucket() {
		ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		when(s3Client.deleteObject(requestCaptor.capture())).thenReturn(DeleteObjectResponse.builder().build());

		storage.delete("item.jpg");

		assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
		assertThat(requestCaptor.getValue().key()).isEqualTo("item.jpg");
	}
}
