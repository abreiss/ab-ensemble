package com.ensemble.stylist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * Parses the raw forced-output JSON into an {@link Outfit}. Blank, {@code null},
	 * or malformed input yields {@link Outfit#empty()}; a missing or mistyped field
	 * yields that field's safe default.
	 *
	 * <p>Two shapes are accepted. The current shape carries a {@code pieces} array of
	 * {@code {itemId, rationale}} objects — ids come from it (in order) and each
	 * rationale is keyed by its id. The legacy shape (a bare {@code itemIds} array,
	 * no per-item rationale) is still accepted when {@code pieces} is absent or not an
	 * array, keeping older responses and fixtures working.
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
		String reason = reason(root.get("reason"));

		JsonNode pieces = root.get("pieces");
		if (pieces != null && pieces.isArray()) {
			return fromPieces(pieces, reason);
		}
		// Legacy {itemIds, reason} shape — no per-item rationale.
		return new Outfit(itemIds(root.get("itemIds")), reason);
	}

	/**
	 * Builds an outfit from the {@code pieces} array: ids in array order, each
	 * paired with its (possibly blank) rationale. Elements that are not objects or
	 * that lack a textual {@code itemId} are skipped.
	 */
	private static Outfit fromPieces(JsonNode pieces, String reason) {
		List<String> ids = new ArrayList<>();
		Map<String, String> rationaleById = new LinkedHashMap<>();
		for (JsonNode piece : pieces) {
			JsonNode idNode = piece.get("itemId");
			if (idNode != null && idNode.isTextual()) {
				String id = idNode.asText();
				ids.add(id);
				JsonNode rationaleNode = piece.get("rationale");
				rationaleById.put(id,
					(rationaleNode != null && rationaleNode.isTextual()) ? rationaleNode.asText() : "");
			}
		}
		return new Outfit(ids, reason, rationaleById);
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
