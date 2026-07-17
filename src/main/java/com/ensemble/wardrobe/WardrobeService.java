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
 * storage. Owns id generation, the derived photo key, and the not-found rule
 * that every id-based operation shares. The {@link Item} persistence model stays
 * inside this service — callers pass {@link TagRequest} and receive
 * {@link ItemResponse} DTOs, so the {@code @DynamoDbBean} never crosses into the
 * controller layer.
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
	 * Creates an item: generates the id, stores the (compressed) photo under a
	 * derived key, and persists the record. The photo is validated by storage —
	 * invalid bytes raise {@code InvalidImageException}.
	 */
	public ItemResponse create(TagRequest tags, byte[] photoBytes) {
		String itemId = UUID.randomUUID().toString();
		String photoKey = itemId + ".jpg";
		photoStorage.save(photoKey, photoBytes);

		Item item = new Item();
		item.setItemId(itemId);
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

	public List<ItemResponse> list() {
		return repository.findAll().stream().map(ItemMapper::toResponse).toList();
	}

	/** Returns the item as a DTO or throws {@link ItemNotFoundException}. */
	public ItemResponse get(String itemId) {
		return ItemMapper.toResponse(find(itemId));
	}

	/** Replaces the tag fields of an existing item. */
	public ItemResponse updateTags(String itemId, TagRequest tags) {
		Item item = find(itemId);
		ItemMapper.applyTags(item, tags);
		return ItemMapper.toResponse(repository.save(item));
	}

	/**
	 * Removes an existing item and its photo. The record is deleted first: if the
	 * photo delete then fails, the item is already gone (a later get returns 404)
	 * rather than leaving a record whose photo is missing.
	 */
	public void delete(String itemId) {
		Item item = find(itemId);
		repository.deleteById(itemId);
		photoStorage.delete(item.getPhotoKey());
	}

	/**
	 * Records that an item was worn: increments {@code wornCount} (an absent/null
	 * count is treated as 0) and sets {@code lastWorn} to now. Both values are
	 * computed here in application code — never by the model — so wear-history stays
	 * deterministic. A read-modify-write; an unknown id throws
	 * {@link ItemNotFoundException}.
	 */
	public ItemResponse markWorn(String itemId) {
		Item item = find(itemId);
		int count = (item.getWornCount() == null ? 0 : item.getWornCount()) + 1;
		item.setWornCount(count);
		item.setLastWorn(Instant.now());
		return ItemMapper.toResponse(repository.save(item));
	}

	/** Loads the stored photo bytes for an existing item. */
	public byte[] loadPhoto(String itemId) {
		Item item = find(itemId);
		return photoStorage.load(item.getPhotoKey());
	}

	/** Fetches the persistence model for internal use, or throws {@link ItemNotFoundException}. */
	private Item find(String itemId) {
		return repository.findById(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
	}
}
