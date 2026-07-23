package com.ensemble.outfit;

import java.time.Instant;
import java.util.HashSet;
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
 * wardrobe ids from the caller's own wardrobe ({@link WardrobeService#list(String)})
 * and rejects the <em>entire</em> save with {@link InvalidOutfitException} if
 * {@code itemIds} is empty, if {@code source} is outside {@code {ai, manual}}, if any
 * submitted id is not a known wardrobe item, or if {@code itemIds} contains a duplicate
 * (no partial save, no silent drop, no silent dedupe). This guard is the
 * authoritative check (DTO bean-validation at the controller is defense-in-depth),
 * so all its branches live here and are unit-tested to 100%.
 *
 * <p><strong>Per-user scoping (spec #15).</strong> Every operation takes the caller's
 * {@code userId}: {@link #create} grounds against and stamps the caller as owner,
 * {@link #list(String)} returns only the caller's outfits, and {@link #find} rejects
 * any outfit the caller does not own with {@link OutfitNotFoundException} — a cross-user
 * id is indistinguishable from a missing one (non-enumerating 404).
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
	 * Validates and persists a saved outfit owned by {@code userId}. Generates the
	 * {@code outfitId} and {@code createdAt}, applies the grounding guard against the
	 * caller's own wardrobe before any write, and stamps the caller as the owner.
	 */
	public OutfitResponse create(String userId, SaveOutfitRequest request) {
		List<String> itemIds = request.itemIds();
		if (itemIds == null || itemIds.isEmpty()) {
			throw new InvalidOutfitException("an outfit must contain at least one item");
		}
		if (!ALLOWED_SOURCES.contains(request.source())) {
			throw new InvalidOutfitException("source must be 'ai' or 'manual'");
		}
		Set<String> validIds = wardrobeService.list(userId).stream()
			.map(ItemResponse::itemId)
			.collect(Collectors.toSet());
		Set<String> seen = new HashSet<>();
		for (String id : itemIds) {
			if (!validIds.contains(id)) {
				throw new InvalidOutfitException("unknown item id: " + id);
			}
			if (!seen.add(id)) {
				throw new InvalidOutfitException("duplicate item id: " + id);
			}
		}
		SavedOutfit entity = OutfitMapper.toEntity(request, UUID.randomUUID().toString(), Instant.now());
		entity.setUserId(userId);
		return OutfitMapper.toResponse(repository.save(entity));
	}

	/** Returns only the outfits owned by {@code userId} (GSI query, no full-table scan). */
	public List<OutfitResponse> list(String userId) {
		return repository.findByUserId(userId).stream().map(OutfitMapper::toResponse).toList();
	}

	/**
	 * Removes an existing outfit the caller owns; an unknown or unowned id throws
	 * {@link OutfitNotFoundException}.
	 */
	public void delete(String userId, String outfitId) {
		find(userId, outfitId);
		repository.deleteById(outfitId);
	}

	/**
	 * Fetches the persistence model for internal use. Throws {@link OutfitNotFoundException}
	 * when the id is unknown <em>or</em> the outfit is owned by another user — the two cases
	 * are deliberately indistinguishable so ownership failures never enumerate other users'
	 * outfits.
	 */
	private SavedOutfit find(String userId, String outfitId) {
		SavedOutfit outfit = repository.findById(outfitId).orElseThrow(() -> new OutfitNotFoundException(outfitId));
		if (!userId.equals(outfit.getUserId())) {
			throw new OutfitNotFoundException(outfitId);
		}
		return outfit;
	}
}
