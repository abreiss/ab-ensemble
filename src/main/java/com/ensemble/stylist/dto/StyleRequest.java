package com.ensemble.stylist.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
 * <p>Every attacker-controlled field is <strong>bounded</strong> so an oversized
 * or malformed request is rejected with a sanitized {@code 400} on binding —
 * <em>before</em> any cap reservation or Claude call. The {@code @Valid} cascade
 * on {@code history} carries the per-turn text cap into each nested turn. The caps
 * are inclusive (a value exactly at the max is accepted); see
 * {@code ApiExceptionHandler} for the shared {@code MethodArgumentNotValidException}
 * → sanitized-{@code 400} mapping.
 *
 * @param prompt the newest free-text vibe; required and length-capped
 * @param history prior turns to replay before the current vibe; may be null/empty,
 *     turn-count-capped, and validated per-turn via the {@code @Valid} cascade
 */
public record StyleRequest(
	@NotBlank @Size(max = MAX_PROMPT_LENGTH) String prompt,
	@Size(max = MAX_HISTORY_TURNS) @Valid List<StyleTurn> history) {

	/** Max characters accepted in the newest {@code prompt} vibe (inclusive). */
	public static final int MAX_PROMPT_LENGTH = 1000;

	/** Max prior conversation turns accepted in {@code history} (inclusive). */
	public static final int MAX_HISTORY_TURNS = 20;

	/** Max characters accepted in a single {@link StyleTurn#text()} (inclusive). */
	public static final int MAX_TURN_TEXT_LENGTH = 2000;

	/**
	 * One prior conversation turn as plain text. {@code role} is {@code "assistant"}
	 * for a prior pick summary and {@code "user"} for a prior vibe / pushback; text
	 * only, never image bytes. {@code text} is length-capped so a hostile client
	 * cannot inflate cost by resending huge history turns.
	 *
	 * @param role {@code "user"} or {@code "assistant"}
	 * @param text the turn's text content; length-capped
	 */
	public record StyleTurn(String role, @Size(max = MAX_TURN_TEXT_LENGTH) String text) {
	}
}
