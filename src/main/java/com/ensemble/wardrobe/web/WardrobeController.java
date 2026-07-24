package com.ensemble.wardrobe.web;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ensemble.security.web.CurrentUserId;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

import jakarta.validation.Valid;

/**
 * REST API for the wardrobe under {@code /api/items}. Exchanges DTOs only; the
 * persistence model and storage internals never cross this boundary — the
 * service returns {@link ItemResponse}. Error mapping (404 / 400) lives in
 * {@link ApiExceptionHandler}.
 *
 * <p>Every handler resolves the authenticated caller's {@code userId} via
 * {@link CurrentUserId} and forwards it to the service, so all reads and writes are
 * scoped to the caller (spec #15) — a cross-user id returns the same non-enumerating
 * 404 as a missing one.
 */
@RestController
@RequestMapping("/api/items")
public class WardrobeController {

	private final WardrobeService service;

	public WardrobeController(WardrobeService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ItemResponse> create(
			@CurrentUserId String userId,
			@RequestPart("photo") MultipartFile photo,
			@Valid TagRequest tags) throws IOException {
		ItemResponse created = service.create(userId, tags, photo.getBytes());
		return ResponseEntity
			.created(URI.create("/api/items/" + created.itemId()))
			.body(created);
	}

	@GetMapping
	public List<ItemResponse> list(@CurrentUserId String userId) {
		return service.list(userId);
	}

	@GetMapping("/{id}")
	public ItemResponse get(@CurrentUserId String userId, @PathVariable String id) {
		return service.get(userId, id);
	}

	@GetMapping("/{id}/photo")
	public ResponseEntity<byte[]> photo(@CurrentUserId String userId, @PathVariable String id) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.body(service.loadPhoto(userId, id));
	}

	@PutMapping(value = "/{id}/tags", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ItemResponse updateTags(
			@CurrentUserId String userId, @PathVariable String id, @Valid @RequestBody TagRequest tags) {
		return service.updateTags(userId, id, tags);
	}

	@PostMapping("/{id}/worn")
	public ItemResponse markWorn(@CurrentUserId String userId, @PathVariable String id) {
		return service.markWorn(userId, id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId String userId, @PathVariable String id) {
		service.delete(userId, id);
	}
}
