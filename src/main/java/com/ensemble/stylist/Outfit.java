package com.ensemble.stylist;

import java.util.List;
import java.util.Map;

/**
 * A stylist pick: the chosen item ids, the single whole-look reason, and a
 * <strong>per-item rationale</strong> keyed by item id. Used both for the raw
 * parsed model output (before grounding) and for the final grounded result
 * (after id-validation drops any hallucinated id, and its rationale with it).
 *
 * <p>An {@link #empty()} outfit — no ids, blank reason, no rationale —
 * represents "no usable pick", the safe value the parser returns for
 * malformed/absent output. All three fields are always non-null so callers never
 * guard against {@code null}.
 *
 * @param itemIds the picked item ids, in order; never {@code null} (possibly empty)
 * @param reason the whole-look explanation; never {@code null} (possibly empty)
 * @param rationaleById a short reason per item id; never {@code null} (possibly empty)
 */
public record Outfit(List<String> itemIds, String reason, Map<String, String> rationaleById) {

	private static final Outfit EMPTY = new Outfit(List.of(), "", Map.of());

	public Outfit {
		itemIds = (itemIds == null) ? List.of() : List.copyOf(itemIds);
		reason = (reason == null) ? "" : reason;
		rationaleById = (rationaleById == null) ? Map.of() : Map.copyOf(rationaleById);
	}

	/**
	 * Back-compatible constructor for a pick with no per-item rationale (empty
	 * wardrobe replies and the legacy {@code {itemIds, reason}} shape).
	 */
	public Outfit(List<String> itemIds, String reason) {
		this(itemIds, reason, Map.of());
	}

	/** The no-pick outfit: empty ids, blank reason, no rationale. */
	public static Outfit empty() {
		return EMPTY;
	}

	/** The rationale for {@code itemId}, or blank if none (never {@code null}). */
	public String rationaleFor(String itemId) {
		return rationaleById.getOrDefault(itemId, "");
	}
}
