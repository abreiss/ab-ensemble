package com.ensemble.stylist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
	 * Builds a grounded outfit for the given vibe.
	 *
	 * @param vibe the free-text style request
	 * @return an outfit of owned item ids + a reason; an empty outfit (with a friendly
	 *     reason) when the wardrobe is empty
	 * @throws StylistUnavailableException on an upstream failure or an ungroundable pick
	 */
	public Outfit style(String vibe) {
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
			pick = pickWithOneRetry(vibe, wardrobeText, validIds);
		} catch (RuntimeException e) {
			// Claude error/timeout (or any unexpected client failure) — degrade gracefully.
			throw new StylistUnavailableException("The stylist is unavailable right now.", e);
		}

		List<String> grounded = pick.itemIds().stream().filter(validIds::contains).toList();
		if (grounded.isEmpty()) {
			throw new StylistUnavailableException(
				"Couldn't build a grounded outfit from your wardrobe. Please try again.");
		}
		return new Outfit(grounded, pick.reason());
	}

	/**
	 * One pick, plus a single retry when the first pick names any id outside the
	 * wardrobe. The retry feeds the specific invalid id(s) back to the model.
	 */
	private Outfit pickWithOneRetry(String vibe, String wardrobeText, Set<String> validIds) {
		List<StylistMessage> conversation = new ArrayList<>();
		conversation.add(StylistMessage.user(vibe));

		Outfit pick = OutfitParser.parse(model.proposeOutfit(wardrobeText, conversation));

		List<String> invalid = invalidIds(pick, validIds);
		if (!invalid.isEmpty()) {
			conversation.add(StylistMessage.assistant("I suggested item ids: " + String.join(", ", pick.itemIds())));
			conversation.add(StylistMessage.user(
				"These item ids are not in the wardrobe: " + String.join(", ", invalid)
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
