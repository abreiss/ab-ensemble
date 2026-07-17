package com.ensemble.stylist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * The stylist agent: turns a free-text vibe into a grounded outfit built only from
 * clothes the user owns. It fetches the wardrobe (text tags + wear-history, never
 * image bytes), asks the model via the {@link StylistModelClient} seam, then
 * enforces the <strong>grounding guardrail</strong>:
 *
 * <ul>
 *   <li>Every returned id must exist in the wardrobe.</li>
 *   <li>If any id is hallucinated, the specific invalid id(s) are fed back and the
 *       pick is retried <strong>exactly once</strong>.</li>
 *   <li>Only the validated subset is rendered — a still-invalid id is dropped, never
 *       returned. If zero valid ids remain, a {@link StylistUnavailableException} is
 *       raised so an unvalidated id is never surfaced.</li>
 * </ul>
 *
 * <p>An empty wardrobe short-circuits to a friendly empty outfit without invoking
 * the model. Upstream failures and ungroundable results degrade to
 * {@link StylistUnavailableException}; nothing here throws an unhandled error.
 *
 * <p>This class and {@link OutfitParser} are the slice's critical logic (100%
 * branch coverage), exercised against a mocked seam in {@code StylistServiceTest}.
 */
@Service
public class StylistService {

	/** Friendly reason returned for an empty wardrobe (a normal 200, not an error). */
	static final String EMPTY_WARDROBE_REASON =
		"Your wardrobe is empty — add a few pieces and I'll style you an outfit.";

	private final StylistModelClient model;
	private final WardrobeService wardrobe;

	public StylistService(StylistModelClient model, WardrobeService wardrobe) {
		this.model = model;
		this.wardrobe = wardrobe;
	}

	/**
	 * Builds a grounded outfit for the given vibe — a single-turn request with no
	 * prior conversation. Delegates to {@link #style(String, List)} with an empty
	 * history.
	 *
	 * @param vibe the free-text style request
	 * @return an outfit of owned item ids + a reason; an empty outfit (with a friendly
	 *     reason) when the wardrobe is empty
	 * @throws StylistUnavailableException on an upstream failure or an ungroundable pick
	 */
	public Outfit style(String vibe) {
		return style(vibe, List.of());
	}

	/**
	 * Builds a grounded outfit for the given vibe within an ongoing conversation.
	 *
	 * <p>The server is <strong>stateless</strong>: the client resends the whole
	 * {@code history} (prior vibe/pick turns, text only) on every re-pick, and the
	 * current {@code vibe} is appended as the newest user turn. The
	 * <strong>grounding guardrail</strong> (id-validation + one retry) runs
	 * unchanged on each pick, so a pushback re-pick is held to the same standard as
	 * the first look. When the history contains a prior assistant turn the model
	 * seam is nudged to produce a <em>different</em> look (see
	 * {@code AnthropicStylistModelClient}).
	 *
	 * @param vibe the newest free-text style request (pushback / "show me another")
	 * @param history prior conversation turns to replay before the current vibe;
	 *     text only, never image bytes
	 * @return an outfit of owned item ids + a reason; an empty outfit (with a friendly
	 *     reason) when the wardrobe is empty
	 * @throws StylistUnavailableException on an upstream failure or an ungroundable pick
	 */
	public Outfit style(String vibe, List<StylistMessage> history) {
		List<ItemResponse> items = wardrobe.list();
		if (items.isEmpty()) {
			return new Outfit(List.of(), EMPTY_WARDROBE_REASON);
		}

		String wardrobeText = renderWardrobe(items);
		Set<String> validIds = new LinkedHashSet<>();
		for (ItemResponse item : items) {
			validIds.add(item.itemId());
		}

		Outfit pick;
		try {
			pick = pickWithOneRetry(vibe, history, wardrobeText, validIds);
		} catch (RuntimeException e) {
			// Claude error/timeout (or any unexpected client failure) — degrade gracefully.
			throw new StylistUnavailableException("The stylist is unavailable right now.", e);
		}

		List<String> grounded = pick.itemIds().stream().filter(validIds::contains).toList();
		if (grounded.isEmpty()) {
			throw new StylistUnavailableException(
				"Couldn't build a grounded outfit from your wardrobe. Please try again.");
		}
		// Carry only the grounded ids' rationale — a hallucinated id's rationale is dropped
		// with the id itself, so nothing unvalidated is ever surfaced.
		Map<String, String> groundedRationale = new LinkedHashMap<>();
		for (String id : grounded) {
			String rationale = pick.rationaleFor(id);
			if (!rationale.isEmpty()) {
				groundedRationale.put(id, rationale);
			}
		}
		return new Outfit(grounded, pick.reason(), groundedRationale);
	}

	/**
	 * One pick, plus a single retry when the first pick names any id outside the
	 * wardrobe. The retry feeds the specific invalid id(s) back to the model. The
	 * conversation is seeded with the prior {@code history} turns, then the current
	 * vibe, so a re-pick reasons over the full accumulated thread.
	 */
	private Outfit pickWithOneRetry(
			String vibe, List<StylistMessage> history, String wardrobeText, Set<String> validIds) {
		List<StylistMessage> conversation = new ArrayList<>(history);
		conversation.add(StylistMessage.user(vibe));

		Outfit pick = OutfitParser.parse(model.proposeOutfit(wardrobeText, conversation));

		List<String> invalid = invalidIds(pick, validIds);
		if (!invalid.isEmpty()) {
			// Correct the grounding with a single USER turn — never an assistant turn.
			// The model client reads a prior assistant turn as a pushback re-pick and
			// would inject the "produce a different outfit" nudge; a grounding retry of a
			// first pick is not a re-pick, so it must not add one.
			conversation.add(StylistMessage.user(
				"Your previous suggestion included item ids that are not in the wardrobe: "
					+ String.join(", ", invalid)
					+ ". Choose only from the itemIds returned by searchWardrobe."));
			pick = OutfitParser.parse(model.proposeOutfit(wardrobeText, conversation));
		}
		return pick;
	}

	/** The picked ids that do not exist in the wardrobe (empty when all are grounded). */
	private static List<String> invalidIds(Outfit pick, Set<String> validIds) {
		return pick.itemIds().stream().filter(id -> !validIds.contains(id)).toList();
	}

	/**
	 * Renders the wardrobe as the {@code searchWardrobe} tool payload: item ids +
	 * text tags + wear-history, one line per item. The {@code photoUrl} is
	 * deliberately excluded — the model reasons over text only and never needs the
	 * photo location, keeping the payload byte-free.
	 */
	private static String renderWardrobe(List<ItemResponse> items) {
		StringBuilder sb = new StringBuilder();
		for (ItemResponse item : items) {
			sb.append("- itemId=").append(item.itemId())
				.append(" | category=").append(nullSafe(item.category()))
				.append(" | primaryColor=").append(nullSafe(item.primaryColor()))
				.append(" | secondaryColor=").append(nullSafe(item.secondaryColor()))
				.append(" | formality=").append(nullSafe(item.formality()))
				.append(" | pattern=").append(nullSafe(item.pattern()))
				.append(" | warmth=").append(nullSafe(item.warmth()))
				.append(" | descriptors=").append(nullSafe(item.descriptors()))
				.append(" | lastWorn=").append(nullSafe(item.lastWorn()))
				.append(" | wornCount=").append(nullSafe(item.wornCount()))
				.append('\n');
		}
		return sb.toString();
	}

	private static String nullSafe(Object value) {
		return value == null ? "unknown" : value.toString();
	}
}
