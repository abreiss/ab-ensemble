package com.ensemble.tagging;

/**
 * Narrow, mockable seam over the Claude vision model. Deliberately SDK-free: it
 * takes JPEG bytes and returns the model's raw structured-tag JSON text (or
 * {@code null} when the model returned no structured block). This is the single
 * boundary where image bytes leave the app to Claude — the stylist path
 * (issue #6) reuses this seam pattern but for text only.
 *
 * <p>Implementations may throw (unchecked) on API error or timeout;
 * {@code TaggingService} owns the graceful fallback, so callers here do not need
 * to catch anything to stay non-blocking.
 */
public interface VisionModelClient {

	/**
	 * Sends one vision request for the given JPEG and returns the model's raw
	 * structured-tag JSON, or {@code null} if no structured output was produced.
	 *
	 * @param jpegImage the (already downsized) JPEG bytes to tag
	 * @return raw JSON text of the tag fields, or {@code null}
	 */
	String extractTagsJson(byte[] jpegImage);
}
