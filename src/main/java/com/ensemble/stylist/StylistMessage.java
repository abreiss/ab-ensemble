package com.ensemble.stylist;

/**
 * One turn of the stylist conversation as plain text. Deliberately SDK-free so
 * {@code StylistService}'s guardrail logic (and the one-retry feedback turn) can
 * be built and unit-tested without the Anthropic SDK; the
 * {@link StylistModelClient} implementation translates these into SDK message
 * params. Carrying only text is also the structural guarantee that <strong>no
 * image bytes</strong> ever reach the stylist model.
 *
 * @param role who authored the turn
 * @param text the turn's text content
 */
public record StylistMessage(Role role, String text) {

	/** The author of a conversation turn. */
	public enum Role {
		USER,
		ASSISTANT
	}

	/** A user turn (the vibe, or the invalid-id correction on retry). */
	public static StylistMessage user(String text) {
		return new StylistMessage(Role.USER, text);
	}

	/** An assistant turn (the model's prior pick, replayed so the retry has context). */
	public static StylistMessage assistant(String text) {
		return new StylistMessage(Role.ASSISTANT, text);
	}
}
