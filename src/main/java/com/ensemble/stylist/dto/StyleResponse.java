package com.ensemble.stylist.dto;

import java.util.List;

/**
 * Outbound stylist result: the grounded item ids, the whole-look reason, and a
 * render-ready {@link OutfitItem} per id. Only owned, validated ids ever appear
 * here. An empty-wardrobe response carries empty {@code itemIds}/{@code items}
 * plus an explanatory {@code reason}.
 *
 * <p>A DTO at the boundary — no Claude client, DynamoDB item, or storage internals
 * leak through.
 *
 * @param itemIds the grounded item ids, in outfit order
 * @param reason the stylist's explanation of why the pieces work together
 * @param items per-item render data, parallel to {@code itemIds}
 */
public record StyleResponse(List<String> itemIds, String reason, List<OutfitItem> items) {

	/**
	 * One rendered piece of the outfit — everything the spec sheet draws. The
	 * {@code rationale} is the stylist's per-item reason (LLM output); the remaining
	 * tag fields are the item's stored tags (deterministic, not LLM-derived) used to
	 * render the name, slot, color swatch, and formality/warmth pips.
	 *
	 * @param itemId the owned item's id
	 * @param photoUrl the URL the client fetches the stored photo from
	 * @param rationale the stylist's one-line reason this piece is in the look
	 * @param category the item's category (drives the slot label)
	 * @param primaryColor the item's primary color (drives the color swatch)
	 * @param formality the item's formality 1–5 (drives the FORM pips)
	 * @param warmth the item's warmth 1–3 (drives the WARM pips)
	 * @param descriptors the item's free-text descriptors (help derive the name)
	 */
	public record OutfitItem(
		String itemId,
		String photoUrl,
		String rationale,
		String category,
		String primaryColor,
		Integer formality,
		Integer warmth,
		List<String> descriptors) {
	}
}
