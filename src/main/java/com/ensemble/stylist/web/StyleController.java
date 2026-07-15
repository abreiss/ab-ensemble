package com.ensemble.stylist.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.stylist.Outfit;
import com.ensemble.stylist.StylistService;
import com.ensemble.stylist.dto.StyleRequest;
import com.ensemble.stylist.dto.StyleResponse;
import com.ensemble.stylist.dto.StyleResponse.OutfitItem;

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
 */
@RestController
@RequestMapping("/api/style")
public class StyleController {

	private final StylistService service;

	public StyleController(StylistService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public StyleResponse style(@RequestBody StyleRequest request) {
		Outfit outfit = service.style(request.prompt());
		List<OutfitItem> items = outfit.itemIds().stream()
			.map(id -> new OutfitItem(id, "/api/items/" + id + "/photo"))
			.toList();
		return new StyleResponse(outfit.itemIds(), outfit.reason(), items);
	}
}
