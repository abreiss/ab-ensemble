package com.ensemble.wardrobe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.dto.ItemMapper;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

/**
 * Wardrobe business logic: coordinates the repository (item records) and photo
 * storage. Owns id generation, the derived photo key, and the ownership rule
 * that every id-based operation shares. The {@link Item} persistence model stays
 * inside this service — callers pass {@link TagRequest} and receive
 * {@link ItemResponse} DTOs, so the {@code @DynamoDbBean} never crosses into the
 * controller layer.
 *
 * <p><strong>Per-user scoping (spec #15).</strong> Every operation takes the
 * authenticated caller's {@code userId}: {@link #create} stamps it as the owner,
 * {@link #list(String)} returns only that owner's items via the {@code userId-index}
 * GSI, and the single {@link #find(String, String)} choke point rejects any id the
 * caller does not own with {@link ItemNotFoundException} — so a cross-user id is
 * indistinguishable from a missing one (non-enumerating 404), never that item's
 * contents.
 */
@Service
public class WardrobeService {

	private final WardrobeRepository repository;
	private final PhotoStorage photoStorage;

	public WardrobeService(WardrobeRepository repository, PhotoStorage photoStorage) {
		this.repository = repository;
		this.photoStorage = photoStorage;
	}

	/**
	 * Creates an item owned by {@code userId}: generates the id, stamps the owner,
	 * stores the (compressed) photo under a derived key, and persists the record.
	 * The photo is validated by storage — invalid bytes raise
	 * {@code InvalidImageException}.
	 */
	public ItemResponse create(String userId, TagRequest tags, byte[] photoBytes) {
		String itemId = UUID.randomUUID().toString();
		String photoKey = itemId + ".jpg";
		photoStorage.save(photoKey, photoBytes);

		Item item = new Item();
		item.setItemId(itemId);
		item.setUserId(userId);
		item.setPhotoKey(photoKey);
		item.setCreatedAt(Instant.now());
		item.setWornCount(0);
		ItemMapper.applyTags(item, tags);
		try {
			return ItemMapper.toResponse(repository.save(item));
		} catch (RuntimeException e) {
			// Record save failed after the photo was written — remove the orphan so
			// create stays all-or-nothing.
			photoStorage.delete(photoKey);
			throw e;
		}
	}

	/**
	 * Unscoped list of every item. Retained only for the stylist tool-loop until
	 * Unit 4 scopes it; callers that represent a single user must use
	 * {@link #list(String)}.
	 */
	public List<ItemResponse> list() {
		return repository.findAll().stream().map(ItemMapper::toResponse).toList();
	}

	/** Returns only the items owned by {@code userId} (GSI query, no full-table scan). */
	public List<ItemResponse> list(String userId) {
		return repository.findByUserId(userId).stream().map(ItemMapper::toResponse).toList();
	}

	/** Returns the caller's item as a DTO or throws {@link ItemNotFoundException}. */
	public ItemResponse get(String userId, String itemId) {
		return ItemMapper.toResponse(find(userId, itemId));
	}

	/** Replaces the tag fields of an existing item the caller owns. */
	public ItemResponse updateTags(String userId, String itemId, TagRequest tags) {
		Item item = find(userId, itemId);
		ItemMapper.applyTags(item, tags);
		return ItemMapper.toResponse(repository.save(item));
	}

	/**
	 * Removes an existing item the caller owns and its photo. The record is deleted
	 * first: if the photo delete then fails, the item is already gone (a later get
	 * returns 404) rather than leaving a record whose photo is missing.
	 */
	public void delete(String userId, String itemId) {
		Item item = find(userId, itemId);
		repository.deleteById(itemId);
		photoStorage.delete(item.getPhotoKey());
	}

	/**
	 * Records that the caller's item was worn: increments {@code wornCount} (an
	 * absent/null count is treated as 0) and sets {@code lastWorn} to now. Both values
	 * are computed here in application code — never by the model — so wear-history stays
	 * deterministic. A read-modify-write; an unknown or unowned id throws
	 * {@link ItemNotFoundException}.
	 */
	public ItemResponse markWorn(String userId, String itemId) {
		Item item = find(userId, itemId);
		int count = (item.getWornCount() == null ? 0 : item.getWornCount()) + 1;
		item.setWornCount(count);
		item.setLastWorn(Instant.now());
		return ItemMapper.toResponse(repository.save(item));
	}

	/** Loads the stored photo bytes for an existing item the caller owns. */
	public byte[] loadPhoto(String userId, String itemId) {
		Item item = find(userId, itemId);
		return photoStorage.load(item.getPhotoKey());
	}

	/**
	 * Fetches the persistence model for internal use. Throws {@link ItemNotFoundException}
	 * when the id is unknown <em>or</em> the item is owned by another user — the two cases
	 * are deliberately indistinguishable so ownership failures never enumerate other users'
	 * items.
	 */
	private Item find(String userId, String itemId) {
		Item item = repository.findById(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
		if (!userId.equals(item.getUserId())) {
			throw new ItemNotFoundException(itemId);
		}
		return item;
	}
}
