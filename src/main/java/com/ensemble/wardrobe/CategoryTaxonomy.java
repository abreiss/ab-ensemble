package com.ensemble.wardrobe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe's single backend source of truth for the item category
 * taxonomy: the ordered list of taxonomy values, and the pure
 * {@link #normalize(String)} fallback that every save path runs a category
 * through so persisted data always lands on a taxonomy value or
 * {@code "Other"}.
 *
 * <p>{@code normalize} is critical logic (AGENTS.md / TESTING.md): it is
 * case- and whitespace-insensitive, maps a starter set of legacy/free-text
 * synonyms onto their taxonomy bucket, and <strong>never throws</strong> —
 * an unrecognized, blank, or {@code null} value simply buckets to
 * {@code "Other"}. This is the code-side guarantee behind the vision
 * tool-schema {@code enum}, which is only an advisory hint to the model
 * (see {@code AnthropicVisionModelClient.tagTool()}).
 *
 * <p>This constant is the backend half of a per-stack "two derived lists,
 * one intent" pair; the frontend defines the same taxonomy independently in
 * {@code frontend/src/lib/categoryTaxonomy.ts}. Cross-stack agreement between
 * the two is maintained <strong>by hand</strong> — no shared code, codegen, or
 * cross-stack test enforces it. A value or synonym added on one side only
 * (e.g. {@code romper → Dress}) silently diverges backend save-path
 * normalization from frontend edit/display-time normalization, and no test
 * fails. This is an explicitly accepted demo-scale trade-off: if you change
 * the taxonomy or synonym map here, mirror it in the frontend file in the
 * same change. Revisit with a shared JSON fixture or CI guard if the taxonomy
 * grows — see assumption A6.1 in
 * {@code docs/specs/18-spec-wardrobe-categories/18-assumptions-wardrobe-categories.md}.
 */
public final class CategoryTaxonomy {

	/** Ordered taxonomy values; {@code Other} is always last. */
	private static final List<String> VALUES =
		List.of("Jacket", "Top", "Bottom", "Dress", "Shoes", "Jewelry", "Accessory", "Other");

	private static final String OTHER = "Other";

	/**
	 * Case/whitespace-insensitive synonym → taxonomy-bucket map. Keys are
	 * lower-case; values are the canonical taxonomy strings. A starter map
	 * (assumption A1.12) covering the canonical values themselves plus
	 * representative legacy/free-text synonyms actually seen in wardrobe data.
	 */
	private static final Map<String, String> SYNONYMS = buildSynonyms();

	private CategoryTaxonomy() {
	}

	/** The ordered taxonomy values, {@code Other} last. Unmodifiable. */
	public static List<String> values() {
		return VALUES;
	}

	/**
	 * Maps a raw, possibly free-text category to a canonical taxonomy value.
	 * Case- and whitespace-insensitive; unrecognized, blank, or {@code null}
	 * input maps to {@code "Other"}. Never throws.
	 *
	 * @param rawCategory the raw stored/model-emitted/user-entered category
	 * @return a value from {@link #values()}, never {@code null}
	 */
	public static String normalize(String rawCategory) {
		if (rawCategory == null) {
			return OTHER;
		}
		String key = rawCategory.trim().toLowerCase(java.util.Locale.ROOT);
		if (key.isEmpty()) {
			return OTHER;
		}
		return SYNONYMS.getOrDefault(key, OTHER);
	}

	private static Map<String, String> buildSynonyms() {
		Map<String, String> map = new LinkedHashMap<>();
		put(map, "Jacket", "jacket", "coat", "blazer", "parka", "overcoat");
		put(map, "Top", "top", "shirt", "t-shirt", "tshirt", "tee", "sweater", "sweatshirt",
			"hoodie", "blouse", "polo", "knit");
		put(map, "Bottom", "bottom", "pants", "chinos", "jeans", "trousers", "shorts", "skirt", "leggings");
		put(map, "Dress", "dress", "gown");
		put(map, "Shoes", "shoes", "shoe", "sneakers", "boots", "loafers", "sandals", "heels", "footwear");
		put(map, "Jewelry", "jewelry", "jewellery", "necklace", "ring", "bracelet", "earrings", "watch");
		put(map, "Accessory", "accessory", "bag", "tote", "hat", "cap", "belt", "scarf",
			"sunglasses", "gloves", "tie");
		put(map, "Other", "other");
		return Map.copyOf(map);
	}

	private static void put(Map<String, String> map, String bucket, String... synonyms) {
		for (String synonym : synonyms) {
			map.put(synonym, bucket);
		}
	}
}
