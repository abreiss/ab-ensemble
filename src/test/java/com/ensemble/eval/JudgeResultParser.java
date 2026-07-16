package com.ensemble.eval;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the Opus judge's structured verdict into a {@link JudgeVerdict}. Even though the
 * judge is asked for forced structured output, the parser is defensive: blank / non-JSON /
 * missing-winner / unexpected-winner all raise {@link IllegalArgumentException}, and a missing
 * {@code perField} or {@code reason} degrades to empty rather than throwing. This defensiveness
 * is the tested logic. Eval harness only.
 */
public final class JudgeResultParser {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * A blind verdict. {@code winner} is one of {@code "A"}, {@code "B"}, {@code "tie"} — the
	 * A/B → model mapping is de-anonymized by the caller (see {@link JudgeTally}).
	 */
	public record JudgeVerdict(String winner, Map<String, String> perField, String reason) {
	}

	private JudgeResultParser() {
	}

	public static JudgeVerdict parse(String json) {
		if (json == null || json.isBlank()) {
			throw new IllegalArgumentException("judge output was blank");
		}
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("judge output was not valid JSON", e);
		}
		JsonNode winnerNode = root.get("winner");
		if (winnerNode == null || !winnerNode.isTextual()) {
			throw new IllegalArgumentException("judge output missing textual 'winner'");
		}
		String winner = normalizeWinner(winnerNode.asText());

		Map<String, String> perField = new LinkedHashMap<>();
		JsonNode pf = root.get("perField");
		if (pf != null && pf.isObject()) {
			for (Map.Entry<String, JsonNode> e : pf.properties()) {
				if (e.getValue().isTextual()) {
					perField.put(e.getKey(), e.getValue().asText());
				}
			}
		}

		JsonNode reasonNode = root.get("reason");
		String reason = (reasonNode != null && reasonNode.isTextual()) ? reasonNode.asText() : "";

		return new JudgeVerdict(winner, perField, reason);
	}

	private static String normalizeWinner(String raw) {
		return switch (raw.trim().toLowerCase()) {
			case "a" -> "A";
			case "b" -> "B";
			case "tie" -> "tie";
			default -> throw new IllegalArgumentException("unexpected winner value: " + raw);
		};
	}
}
