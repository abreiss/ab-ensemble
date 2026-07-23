package com.ensemble.outfit.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ensemble.outfit.OutfitService;
import com.ensemble.outfit.dto.OutfitResponse;
import com.ensemble.outfit.dto.SaveOutfitRequest;
import com.ensemble.security.web.CurrentUserId;

import jakarta.validation.Valid;

/**
 * REST API for saved outfits under {@code /api/outfits}. Exchanges DTOs only; the
 * persistence model never crosses this boundary — the service returns
 * {@link OutfitResponse}. Error mapping (grounding/validation failure → 400,
 * delete-unknown → 404) lives in
 * {@code com.ensemble.wardrobe.web.ApiExceptionHandler}, which registers this
 * controller for the shared sanitized error shape. The routes are session-gated
 * automatically by the servlet filter on {@code /api/*}.
 *
 * <p>Each handler resolves the authenticated caller's {@code userId} via
 * {@link CurrentUserId} and forwards it to the service, so saves are grounded against
 * and stamped with the caller, list returns only the caller's outfits, and a cross-user
 * delete returns the same non-enumerating 404 as a missing one (spec #15).
 */
@RestController
@RequestMapping("/api/outfits")
public class OutfitController {

	private final OutfitService service;

	public OutfitController(OutfitService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<OutfitResponse> save(
			@CurrentUserId String userId, @Valid @RequestBody SaveOutfitRequest request) {
		OutfitResponse created = service.create(userId, request);
		return ResponseEntity
			.created(URI.create("/api/outfits/" + created.outfitId()))
			.body(created);
	}

	@GetMapping
	public List<OutfitResponse> list(@CurrentUserId String userId) {
		return service.list(userId);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId String userId, @PathVariable String id) {
		service.delete(userId, id);
	}
}
