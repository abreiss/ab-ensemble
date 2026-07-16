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

import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

import jakarta.validation.Valid;

/**
 * REST API for the wardrobe under {@code /api/items}. Exchanges DTOs only; the
 * persistence model and storage internals never cross this boundary — the
 * service returns {@link ItemResponse}. Error mapping (404 / 400) lives in
 * {@link ApiExceptionHandler}.
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
			@RequestPart("photo") MultipartFile photo,
			@Valid TagRequest tags) throws IOException {
		ItemResponse created = service.create(tags, photo.getBytes());
		return ResponseEntity
			.created(URI.create("/api/items/" + created.itemId()))
			.body(created);
	}

	@GetMapping
	public List<ItemResponse> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	public ItemResponse get(@PathVariable String id) {
		return service.get(id);
	}

	@GetMapping("/{id}/photo")
	public ResponseEntity<byte[]> photo(@PathVariable String id) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.body(service.loadPhoto(id));
	}

	@PutMapping(value = "/{id}/tags", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ItemResponse updateTags(@PathVariable String id, @Valid @RequestBody TagRequest tags) {
		return service.updateTags(id, tags);
	}

	@PostMapping("/{id}/worn")
	public ItemResponse markWorn(@PathVariable String id) {
		return service.markWorn(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		service.delete(id);
	}
}
