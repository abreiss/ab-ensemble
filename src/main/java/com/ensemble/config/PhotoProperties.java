package com.ensemble.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Photo storage settings. Bound from {@code ensemble.photos.*}.
 *
 * @param dir base directory for {@code LocalDiskPhotoStorage}
 * @param maxUploadPixels reject an upload whose decoded pixel count
 *     (width × height) would exceed this, guarding against decompression-bomb
 *     images that are small compressed but huge in memory. Defaults to
 *     {@value #DEFAULT_MAX_UPLOAD_PIXELS} when unset.
 * @param backend which {@code PhotoStorage} bean is active: {@code disk} or
 *     {@code s3}. Defaults to {@value #DEFAULT_BACKEND} when blank/absent.
 * @param s3 S3-backend settings, used only when {@code backend=s3}
 */
@ConfigurationProperties(prefix = "ensemble.photos")
public record PhotoProperties(String dir, long maxUploadPixels, String backend, S3 s3) {

	/** ~50 megapixels — comfortably above any phone camera, well below a bomb. */
	public static final long DEFAULT_MAX_UPLOAD_PIXELS = 50_000_000L;

	/** Preserves current local-dev behavior when unset. */
	static final String DEFAULT_BACKEND = "disk";

	public PhotoProperties {
		if (maxUploadPixels <= 0) {
			maxUploadPixels = DEFAULT_MAX_UPLOAD_PIXELS;
		}
		if (backend == null || backend.isBlank()) {
			backend = DEFAULT_BACKEND;
		}
		if (s3 == null) {
			s3 = new S3(null);
		}
	}

	/**
	 * @param bucket S3 bucket name for {@code S3PhotoStorage}, bound from
	 *     {@code ensemble.photos.s3.bucket}
	 */
	public record S3(String bucket) {
	}
}
