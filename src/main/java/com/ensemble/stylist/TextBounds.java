package com.ensemble.stylist;

/**
 * Deterministic length backstop for the stylist's free-text output fields. The
 * model-side prompt already asks for a concise {@code reason}/{@code rationale},
 * but a compromised or misbehaving model could still emit an oversized field
 * (the "count to 1000 in the rationale" class of prompt injection). {@link
 * #cap(String, int)} is the app-side guarantee that whatever the model returns is
 * bounded <em>before</em> it crosses the DTO boundary and reaches the client.
 *
 * <p>Package-private and pure — no state, no dependencies — so it is trivially
 * covered to 100% branch and reused by {@code StylistService} when assembling the
 * grounded {@code Outfit}.
 */
final class TextBounds {

	private TextBounds() {
	}

	/**
	 * Returns {@code value} unchanged when it is at or below {@code max} characters,
	 * or its first {@code max} characters when it is longer. {@code null} passes
	 * through as {@code null} so callers can cap an absent field without a guard.
	 *
	 * @param value the text to bound; may be {@code null}
	 * @param max the maximum length to keep (assumed non-negative)
	 * @return the bounded text, never longer than {@code max}
	 */
	static String cap(String value, int max) {
		if (value == null) {
			return null;
		}
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max);
	}
}
