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
 */
@ConfigurationProperties(prefix = "ensemble.photos")
public record PhotoProperties(String dir, long maxUploadPixels) {

	/** ~50 megapixels — comfortably above any phone camera, well below a bomb. */
	public static final long DEFAULT_MAX_UPLOAD_PIXELS = 50_000_000L;

	public PhotoProperties {
		if (maxUploadPixels <= 0) {
			maxUploadPixels = DEFAULT_MAX_UPLOAD_PIXELS;
		}
	}
}
