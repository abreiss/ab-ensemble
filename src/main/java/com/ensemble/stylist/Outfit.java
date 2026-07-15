package com.ensemble.stylist;

import java.util.List;

/**
 * A stylist pick: the chosen item ids and the single human-facing reason. Used
 * both for the raw parsed model output (before grounding) and for the final
 * grounded result (after id-validation drops any hallucinated id).
 *
 * <p>An {@link #empty()} outfit — no ids, blank reason — represents "no usable
 * pick", the safe value the parser returns for malformed/absent output. Both
 * fields are always non-null so callers never guard against {@code null}.
 *
 * @param itemIds the picked item ids, in order; never {@code null} (possibly empty)
 * @param reason the stylist's explanation; never {@code null} (possibly empty)
 */
public record Outfit(List<String> itemIds, String reason) {

	private static final Outfit EMPTY = new Outfit(List.of(), "");

	public Outfit {
		itemIds = (itemIds == null) ? List.of() : List.copyOf(itemIds);
		reason = (reason == null) ? "" : reason;
	}

	/** The no-pick outfit: empty ids and a blank reason. */
	public static Outfit empty() {
		return EMPTY;
	}
}
