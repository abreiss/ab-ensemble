package com.ensemble.stylist;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Defensive parser for the stylist's forced {@code record_outfit} output. Turns
 * the model's raw tool-input JSON into an {@link Outfit}, modeled on
 * {@code TaggingService.map(...)}: it <strong>never throws</strong>. Any
 * blank, malformed, or partially-shaped body degrades to a safe value
 * ({@link Outfit#empty()} or a partial outfit) so a bad model response can never
 * crash the style request.
 *
 * <p>This is critical logic (100% branch coverage): every present/absent/mistyped
 * field path is exercised in {@code OutfitParserTest}.
 */
final class OutfitParser {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private OutfitParser() {
	}

	/**
	 * Parses raw {@code {itemIds, reason}} JSON into an {@link Outfit}. Blank,
	 * {@code null}, or malformed input yields {@link Outfit#empty()}; a missing or
	 * mistyped field yields that field's safe default (empty id list / blank reason).
	 *
	 * @param json the raw tool-input JSON, or {@code null}
	 * @return a never-{@code null} outfit; safe on any bad input
	 */
	static Outfit parse(String json) {
		if (json == null || json.isBlank()) {
			return Outfit.empty();
		}
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			// Malformed body — degrade to no pick rather than propagate.
			return Outfit.empty();
		}
		return new Outfit(itemIds(root.get("itemIds")), reason(root.get("reason")));
	}

	/** The string elements of the {@code itemIds} array; absent/non-array → empty list. */
	private static List<String> itemIds(JsonNode node) {
		if (node == null || !node.isArray()) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		for (JsonNode element : node) {
			if (element.isTextual()) {
				ids.add(element.asText());
			}
		}
		return ids;
	}

	/** The {@code reason} text; absent/non-textual → blank. */
	private static String reason(JsonNode node) {
		return (node != null && node.isTextual()) ? node.asText() : "";
	}
}
