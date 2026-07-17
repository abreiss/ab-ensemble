package com.ensemble.stylist.dto;

import java.util.List;

/**
 * Inbound style request: the free-text vibe the user typed (e.g. "streetwear
 * today") plus an optional ordered {@code history} of prior conversation turns.
 * A DTO at the API boundary — the controller never exposes stylist internals.
 *
 * <p>The server is <strong>stateless</strong>: on a pushback / "show me another"
 * re-pick the client resends the whole prior thread as {@code history}, and the
 * controller maps it to the stylist's text-only conversation turns. A first
 * request omits {@code history} (or sends it empty), keeping the single-turn
 * contract backward compatible.
 *
 * @param prompt the newest free-text vibe
 * @param history prior turns to replay before the current vibe; may be null/empty
 */
public record StyleRequest(String prompt, List<StyleTurn> history) {

	/**
	 * One prior conversation turn as plain text. {@code role} is {@code "assistant"}
	 * for a prior pick summary and {@code "user"} for a prior vibe / pushback; text
	 * only, never image bytes.
	 *
	 * @param role {@code "user"} or {@code "assistant"}
	 * @param text the turn's text content
	 */
	public record StyleTurn(String role, String text) {
	}
}
