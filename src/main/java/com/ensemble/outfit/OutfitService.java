package com.ensemble.outfit;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ensemble.outfit.dto.OutfitMapper;
import com.ensemble.outfit.dto.OutfitResponse;
import com.ensemble.outfit.dto.SaveOutfitRequest;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * Saved-outfit business logic: create/list/delete. Owns id generation and the
 * {@code createdAt} timestamp (server-owned, never the client), and routes
 * id-based lookups through a single not-found choke point ({@link #find}) that
 * throws {@link OutfitNotFoundException}, mirroring {@code WardrobeService.find}.
 *
 * <p>Enforces the <strong>save-time grounding guard</strong> — the synchronous
 * analog of the stylist's grounding guardrail: it builds the set of valid
 * wardrobe ids from {@link WardrobeService#list()} and rejects the <em>entire</em>
 * save with {@link InvalidOutfitException} if {@code itemIds} is empty, if
 * {@code source} is outside {@code {ai, manual}}, or if any submitted id is not a
 * known wardrobe item (no partial save, no silent drop). This guard is the
 * authoritative check (DTO bean-validation at the controller is defense-in-depth),
 * so all its branches live here and are unit-tested to 100%.
 */
@Service
public class OutfitService {

	private static final Set<String> ALLOWED_SOURCES = Set.of("ai", "manual");

	private final OutfitRepository repository;
	private final WardrobeService wardrobeService;

	public OutfitService(OutfitRepository repository, WardrobeService wardrobeService) {
		this.repository = repository;
		this.wardrobeService = wardrobeService;
	}

	/**
	 * Validates and persists a saved outfit. Generates the {@code outfitId} and
	 * {@code createdAt}, then applies the grounding guard before any write.
	 */
	public OutfitResponse create(SaveOutfitRequest request) {
		List<String> itemIds = request.itemIds();
		if (itemIds == null || itemIds.isEmpty()) {
			throw new InvalidOutfitException("an outfit must contain at least one item");
		}
		if (!ALLOWED_SOURCES.contains(request.source())) {
			throw new InvalidOutfitException("source must be 'ai' or 'manual'");
		}
		Set<String> validIds = wardrobeService.list().stream()
			.map(ItemResponse::itemId)
			.collect(Collectors.toSet());
		for (String id : itemIds) {
			if (!validIds.contains(id)) {
				throw new InvalidOutfitException("unknown item id: " + id);
			}
		}
		SavedOutfit entity = OutfitMapper.toEntity(request, UUID.randomUUID().toString(), Instant.now());
		return OutfitMapper.toResponse(repository.save(entity));
	}

	public List<OutfitResponse> list() {
		return repository.findAll().stream().map(OutfitMapper::toResponse).toList();
	}

	/** Removes an existing outfit; an unknown id throws {@link OutfitNotFoundException}. */
	public void delete(String outfitId) {
		find(outfitId);
		repository.deleteById(outfitId);
	}

	/** Fetches the persistence model for internal use, or throws {@link OutfitNotFoundException}. */
	private SavedOutfit find(String outfitId) {
		return repository.findById(outfitId).orElseThrow(() -> new OutfitNotFoundException(outfitId));
	}
}
