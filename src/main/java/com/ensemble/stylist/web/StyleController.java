package com.ensemble.stylist.web;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.stylist.Outfit;
import com.ensemble.stylist.StylistMessage;
import com.ensemble.stylist.StylistService;
import com.ensemble.stylist.dto.StyleRequest;
import com.ensemble.stylist.dto.StyleRequest.StyleTurn;
import com.ensemble.stylist.dto.StyleResponse;
import com.ensemble.stylist.dto.StyleResponse.OutfitItem;
import com.ensemble.usage.CallCapService;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * REST API for the stylist under {@code /api/style}. Accepts a free-text vibe and
 * returns a grounded outfit as DTOs only — the {@link StylistService} internals,
 * the Claude client, and DynamoDB items never cross this boundary. Each returned
 * id is mapped to the {@code photoUrl} the client fetches to render the look.
 *
 * <p>Empty-wardrobe is a normal {@code 200} with an empty outfit + explanatory
 * reason. Upstream/ungroundable failures surface as
 * {@code StylistUnavailableException}, mapped to a graceful error by
 * {@code ApiExceptionHandler} (which lists this controller in its
 * {@code assignableTypes}).
 *
 * <p>The stylist is <strong>stateless</strong>: on a pushback re-pick the client
 * resends the whole prior thread as {@code history}, which is mapped here to
 * text-only {@link StylistMessage} turns. Omitting {@code history} keeps the
 * original single-turn contract intact.
 */
@RestController
@RequestMapping("/api/style")
public class StyleController {

	private final StylistService service;
	private final CallCapService callCapService;
	private final WardrobeService wardrobe;

	public StyleController(StylistService service, CallCapService callCapService, WardrobeService wardrobe) {
		this.service = service;
		this.callCapService = callCapService;
		this.wardrobe = wardrobe;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public StyleResponse style(@RequestBody StyleRequest request) {
		callCapService.reserve();
		Outfit outfit = service.style(request.prompt(), toHistory(request.history()));
		List<OutfitItem> items = enrich(outfit);
		return new StyleResponse(outfit.itemIds(), outfit.reason(), items);
	}

	/**
	 * Assembles each render-ready {@link OutfitItem}: the stylist's per-item
	 * rationale (from the {@link Outfit}) joined to the item's stored tags (from the
	 * wardrobe, keyed by id). The wardrobe is read once and looked up by id; a
	 * grounded id with no matching item (a benign race) degrades to null tags rather
	 * than failing the request. An empty outfit yields an empty list without a
	 * wardrobe read cost that matters at demo scale.
	 */
	private List<OutfitItem> enrich(Outfit outfit) {
		if (outfit.itemIds().isEmpty()) {
			return List.of();
		}
		Map<String, ItemResponse> byId = wardrobe.list().stream()
			.collect(Collectors.toMap(ItemResponse::itemId, Function.identity(), (first, second) -> first));
		return outfit.itemIds().stream()
			.map(id -> toOutfitItem(id, outfit.rationaleFor(id), byId.get(id)))
			.toList();
	}

	private static OutfitItem toOutfitItem(String id, String rationale, ItemResponse item) {
		String photoUrl = "/api/items/" + id + "/photo";
		if (item == null) {
			return new OutfitItem(id, photoUrl, rationale, null, null, null, null, List.of());
		}
		return new OutfitItem(id, photoUrl, rationale,
			item.category(), item.primaryColor(), item.formality(), item.warmth(), item.descriptors());
	}

	/**
	 * Maps the wire {@link StyleTurn}s to the stylist's text-only conversation
	 * turns. A null/absent history yields an empty list (the single-turn path); a
	 * {@code "assistant"} role (case-insensitive) becomes an assistant turn, and
	 * every other role is treated as a user turn.
	 */
	private static List<StylistMessage> toHistory(List<StyleTurn> turns) {
		if (turns == null) {
			return List.of();
		}
		return turns.stream()
			.map(t -> "assistant".equalsIgnoreCase(t.role())
				? StylistMessage.assistant(t.text())
				: StylistMessage.user(t.text()))
			.toList();
	}
}
