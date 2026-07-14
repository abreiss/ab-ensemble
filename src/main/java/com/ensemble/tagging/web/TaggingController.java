package com.ensemble.tagging.web;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ensemble.tagging.TaggingService;
import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Tag-preview endpoint. {@code POST /api/items/tag} accepts a garment photo as
 * {@code multipart/form-data} (the same {@code photo} part shape as
 * {@code POST /api/items}) and returns a {@link TagSuggestion} as JSON.
 *
 * <p>It <strong>persists nothing</strong> — no item record and no stored photo — so the
 * client can review and edit the suggestion before saving through the existing create
 * endpoint. A degraded or failed vision call still returns {@code 200} with a partial/empty
 * suggestion (the service swallows those); a missing or non-decodable photo becomes a
 * {@code 400} via the shared {@link com.ensemble.wardrobe.web.ApiExceptionHandler}
 * (a {@code MissingServletRequestPartException} or an {@code InvalidImageException}).
 */
@RestController
@RequestMapping("/api/items")
public class TaggingController {

	private final TaggingService service;

	public TaggingController(TaggingService service) {
		this.service = service;
	}

	@PostMapping(value = "/tag", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TagSuggestion tag(@RequestPart("photo") MultipartFile photo) throws IOException {
		return service.suggest(photo.getBytes());
	}
}
