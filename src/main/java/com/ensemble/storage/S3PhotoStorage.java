package com.ensemble.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ensemble.config.PhotoProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores photos in S3 — the deploy-time {@link PhotoStorage} backend, selected
 * via {@code ensemble.photos.backend=s3}. On save, the bytes are validated and
 * resized to a small JPEG by the shared {@link ImageProcessor} (same ≤800px
 * rule as {@code LocalDiskPhotoStorage}), then put under {@code key} in the
 * configured bucket.
 *
 * <p>The {@link S3Client} bean is only built when this backend is active (see
 * {@code StorageConfig}), so disk mode never touches AWS credentials.
 */
@Component
@ConditionalOnProperty(name = "ensemble.photos.backend", havingValue = "s3")
public class S3PhotoStorage implements PhotoStorage {

	private static final String CONTENT_TYPE_JPEG = "image/jpeg";

	private final S3Client s3Client;
	private final String bucket;
	private final ImageProcessor imageProcessor;

	public S3PhotoStorage(S3Client s3Client, PhotoProperties props, ImageProcessor imageProcessor) {
		this.s3Client = s3Client;
		this.bucket = props.s3().bucket();
		this.imageProcessor = imageProcessor;
	}

	@Override
	public void save(String key, byte[] imageBytes) {
		byte[] jpeg = imageProcessor.toResizedJpeg(imageBytes); // validate/resize before any S3 call
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(CONTENT_TYPE_JPEG)
			.build();
		s3Client.putObject(request, RequestBody.fromBytes(jpeg));
	}

	@Override
	public byte[] load(String key) {
		GetObjectRequest request = GetObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
		try {
			return s3Client.getObjectAsBytes(request).asByteArray();
		} catch (NoSuchKeyException e) {
			throw new PhotoNotFoundException(key);
		}
	}

	@Override
	public void delete(String key) {
		DeleteObjectRequest request = DeleteObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build();
		s3Client.deleteObject(request);
	}
}
