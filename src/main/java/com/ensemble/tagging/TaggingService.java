package com.ensemble.tagging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ensemble.storage.ImageProcessor;
import com.ensemble.storage.InvalidImageException;
import com.ensemble.tagging.dto.TagSuggestion;
import com.ensemble.wardrobe.CategoryTaxonomy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns garment image bytes into a structured {@link TagSuggestion}: it downsizes the
 * photo through the shared {@link ImageProcessor}, sends one Haiku vision request via the
 * mockable {@link VisionModelClient} seam, then maps and clamps the model's JSON onto the
 * wardrobe tag shape.
 *
 * <p>There are two deliberately different failure boundaries:
 * <ul>
 *   <li>The image guard runs <strong>first and outside</strong> the try/catch. A
 *       non-decodable or oversized photo raises {@link InvalidImageException}, which
 *       propagates so the endpoint answers {@code 400} — invalid input is the caller's
 *       fault, not a tagging failure.</li>
 *   <li>Everything after (the API call and the JSON parse) is <strong>swallowed</strong>.
 *       Any API error, timeout, or malformed/partial body degrades to a partial or empty
 *       suggestion, so tagging never throws a {@code 500} and never blocks the user from
 *       creating the item by hand.</li>
 * </ul>
 *
 * <p>The mapping and fallback here are the slice's critical logic (100% branch coverage):
 * present/absent fields, numeric range clamping, and every failure mode are exercised
 * against a mocked seam.
 */
@Service
public class TaggingService {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int FORMALITY_MIN = 1;
	private static final int FORMALITY_MAX = 5;
	private static final int WARMTH_MIN = 1;
	private static final int WARMTH_MAX = 3;

	private final VisionModelClient vision;
	private final ImageProcessor imageProcessor;

	public TaggingService(VisionModelClient vision, ImageProcessor imageProcessor) {
		this.vision = vision;
		this.imageProcessor = imageProcessor;
	}

	/**
	 * Suggests tags for the given photo. Rejects a non-image with
	 * {@link InvalidImageException} (the endpoint maps it to {@code 400}); on any downstream
	 * failure returns a partial/empty {@link TagSuggestion} (the endpoint returns {@code 200}).
	 *
	 * @param photo the raw uploaded image bytes
	 * @return the suggested tags (possibly partial/empty), never {@code null}
	 */
	public TagSuggestion suggest(byte[] photo) {
		// Outside the try on purpose: a bad image is a 400, not a swallowed tagging failure.
		byte[] resized = imageProcessor.toResizedJpeg(photo);
		try {
			return map(vision.extractTagsJson(resized));
		} catch (RuntimeException | JsonProcessingException e) {
			// API error, timeout, or malformed body — degrade to an editable empty suggestion.
			return TagSuggestion.empty();
		}
	}

	/**
	 * Maps the model's raw tag JSON onto the tag shape; blank/absent input yields empty.
	 *
	 * <p>Exposed {@code public static} so the offline model-eval harness parses and clamps
	 * tag JSON through this <strong>same</strong> logic rather than re-implementing it.
	 */
	public static TagSuggestion map(String json) throws JsonProcessingException {
		if (json == null || json.isBlank()) {
			return TagSuggestion.empty();
		}
		JsonNode root = MAPPER.readTree(json);
		return new TagSuggestion(
			normalizedCategory(root.get("category")),
			text(root.get("primaryColor")),
			text(root.get("secondaryColor")),
			intInRange(root.get("formality"), FORMALITY_MIN, FORMALITY_MAX),
			text(root.get("pattern")),
			intInRange(root.get("warmth"), WARMTH_MIN, WARMTH_MAX),
			descriptors(root.get("descriptors")));
	}

	/** A textual field, or {@code null} if absent or not a JSON string. */
	private static String text(JsonNode node) {
		return (node != null && node.isTextual()) ? node.asText() : null;
	}

	/**
	 * The model-emitted category, normalized to a taxonomy value so the
	 * suggested pre-fill already matches a valid {@code <select>} option
	 * (the vision tool-schema {@code enum} is only an advisory hint — this is
	 * the code-side guarantee, mirroring {@code ItemMapper.applyTags}). A
	 * genuinely undetermined category (absent or non-textual) is left
	 * {@code null} rather than defaulted to {@code "Other"}, so the form can
	 * still show its unselected placeholder.
	 */
	private static String normalizedCategory(JsonNode node) {
		String raw = text(node);
		return (raw == null) ? null : CategoryTaxonomy.normalize(raw);
	}

	/** An integer field clamped to [{@code min}, {@code max}]; out-of-range/non-int → {@code null}. */
	private static Integer intInRange(JsonNode node, int min, int max) {
		if (node == null || !node.isInt()) {
			return null;
		}
		int value = node.asInt();
		return (value >= min && value <= max) ? value : null;
	}

	/** The string elements of a JSON array field; absent/non-array → {@code null}. */
	private static List<String> descriptors(JsonNode node) {
		if (node == null || !node.isArray()) {
			return null;
		}
		List<String> out = new ArrayList<>();
		for (JsonNode element : node) {
			if (element.isTextual()) {
				out.add(element.asText());
			}
		}
		return out;
	}
}
