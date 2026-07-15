package com.ensemble.stylist.dto;

import java.util.List;

/**
 * Outbound stylist result: the grounded item ids, the single reason, and a
 * render-ready {@link OutfitItem} per id (its id + the {@code photoUrl} the client
 * fetches). Only owned, validated ids ever appear here. An empty-wardrobe response
 * carries empty {@code itemIds}/{@code items} plus an explanatory {@code reason}.
 *
 * <p>A DTO at the boundary — no Claude client, DynamoDB item, or storage internals
 * leak through.
 *
 * @param itemIds the grounded item ids, in outfit order
 * @param reason the stylist's explanation of why the pieces work together
 * @param items per-item render data (id + photo URL), parallel to {@code itemIds}
 */
public record StyleResponse(List<String> itemIds, String reason, List<OutfitItem> items) {

	/**
	 * One rendered piece of the outfit.
	 *
	 * @param itemId the owned item's id
	 * @param photoUrl the URL the client fetches the stored photo from
	 */
	public record OutfitItem(String itemId, String photoUrl) {
	}
}
