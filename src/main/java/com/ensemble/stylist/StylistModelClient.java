package com.ensemble.stylist;

import java.util.List;

/**
 * Narrow, mockable seam over the Claude stylist model — the text-only analogue of
 * {@code com.ensemble.tagging.VisionModelClient}. It runs one styling turn: the
 * model may call the {@code searchWardrobe} tool (answered with the supplied
 * wardrobe text), reasons over the tags, and is then forced to emit the
 * {@code record_outfit} structured output, whose raw JSON is returned for
 * {@code StylistService} to parse defensively.
 *
 * <p>Deliberately SDK-free and <strong>byte-free</strong>: it takes only text
 * (the wardrobe tag summary + the conversation), so it is structurally impossible
 * for image bytes to reach the stylist. Implementations may throw (unchecked) on
 * API error or timeout; {@code StylistService} owns the graceful degrade, so
 * callers here need not catch anything.
 */
public interface StylistModelClient {

	/**
	 * Runs one styling turn and returns the model's raw {@code {itemIds, reason}}
	 * JSON, or {@code null} if no structured output was produced.
	 *
	 * @param wardrobeToolText the text returned when the model calls
	 *     {@code searchWardrobe} — item ids + tags + wear-history, never image bytes
	 * @param conversation the ordered conversation turns (the vibe, plus any
	 *     invalid-id correction on retry); never image bytes
	 * @return raw forced-output JSON, or {@code null}
	 */
	String proposeOutfit(String wardrobeToolText, List<StylistMessage> conversation);
}
