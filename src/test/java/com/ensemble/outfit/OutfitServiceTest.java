package com.ensemble.outfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.outfit.dto.OutfitResponse;
import com.ensemble.outfit.dto.SaveOutfitRequest;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * Unit coverage for {@link OutfitService}, centered on the save-time grounding
 * guard — the critical logic that must reach 100% branch coverage per
 * {@code docs/TESTING.md}: all ids valid → save; any unknown id → reject the whole
 * save; empty {@code itemIds} → reject; {@code source} outside {ai,manual} → reject;
 * delete-unknown → not-found. Per-user scoping (spec #15) is layered on: the guard's
 * valid-id set is built from the caller's <em>own</em> wardrobe
 * ({@link WardrobeService#list(String)}), the created outfit is stamped with the
 * caller's userId, and delete is ownership-checked. The {@link WardrobeService} and
 * {@link OutfitRepository} are mocked so the guard is exercised in isolation.
 */
@ExtendWith(MockitoExtension.class)
class OutfitServiceTest {

	/** The authenticated caller every operation is scoped to (spec #15). */
	private static final String USER = "userA";

	@Mock
	OutfitRepository repository;

	@Mock
	WardrobeService wardrobeService;

	@InjectMocks
	OutfitService service;

	private ItemResponse wardrobeItem(String id) {
		return new ItemResponse(id, "Top", null, null, null, null, null, null,
			"/api/items/" + id + "/photo", Instant.parse("2026-07-13T00:00:00Z"), null, 0);
	}

	private SaveOutfitRequest request(List<String> itemIds, String source, String reason) {
		return new SaveOutfitRequest(itemIds, source, reason);
	}

	private SavedOutfit entity(String id) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(id);
		outfit.setUserId(USER);
		outfit.setItemIds(List.of("a"));
		outfit.setSource("manual");
		outfit.setCreatedAt(Instant.now());
		return outfit;
	}

	@Test
	void create_allIdsValid_persistsWithServerOwnedIdAndCreatedAt() {
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a"), wardrobeItem("b")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OutfitResponse created = service.create(USER, request(List.of("a", "b"), "manual", null));

		assertThat(created.outfitId()).isNotBlank();
		assertThat(created.createdAt()).isNotNull();
		assertThat(created.itemIds()).containsExactly("a", "b");
		assertThat(created.source()).isEqualTo("manual");
		assertThat(created.reason()).isNull();

		ArgumentCaptor<SavedOutfit> captor = ArgumentCaptor.forClass(SavedOutfit.class);
		verify(repository).save(captor.capture());
		SavedOutfit persisted = captor.getValue();
		assertThat(persisted.getOutfitId()).isEqualTo(created.outfitId());
		assertThat(persisted.getCreatedAt()).isNotNull();
		assertThat(persisted.getItemIds()).containsExactly("a", "b");
	}

	@Test
	void create_stampsCallerUserId() {
		// The owner FR: a saved outfit must carry the caller's userId so it is only ever
		// returned to that user. Capture the persisted entity and assert the stamp directly.
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.create(USER, request(List.of("a"), "manual", null));

		ArgumentCaptor<SavedOutfit> captor = ArgumentCaptor.forClass(SavedOutfit.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo(USER);
	}

	@Test
	void create_groundsAgainstCallersOwnWardrobe() {
		// The valid-id set is built from the caller's own wardrobe — an id that exists only in
		// another user's wardrobe is not consulted here, so it grounds like an unknown id.
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a")));

		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(List.of("a", "othersItem"), "manual", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void create_aiSourceWithReason_preservesSourceAndReason() {
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		OutfitResponse created = service.create(USER, request(List.of("a"), "ai", "navy blazer anchors it"));

		assertThat(created.source()).isEqualTo("ai");
		assertThat(created.reason()).isEqualTo("navy blazer anchors it");
	}

	@Test
	void create_unknownItemId_rejectsEntireSaveAndPersistsNothing() {
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a")));

		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(List.of("a", "ghost"), "manual", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void create_emptyItemIds_rejectsWithoutConsultingWardrobe() {
		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(List.of(), "manual", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void create_nullItemIds_rejects() {
		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(null, "manual", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void create_sourceOutsideAllowedSet_rejects() {
		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(List.of("a"), "robot", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void create_duplicateItemIds_rejectsEntireSaveAndPersistsNothing() {
		when(wardrobeService.list(USER)).thenReturn(List.of(wardrobeItem("a")));

		assertThatExceptionOfType(InvalidOutfitException.class)
			.isThrownBy(() -> service.create(USER, request(List.of("a", "a"), "manual", null)));
		verify(repository, never()).save(any());
	}

	@Test
	void list_returnsOnlyCallersOutfits() {
		// The scoped list delegates to the userId GSI query, so it can only ever return the
		// caller's saved outfits — no full-table scan, no other user's outfits.
		when(repository.findByUserId(USER)).thenReturn(List.of(entity("o1"), entity("o2")));

		List<OutfitResponse> list = service.list(USER);

		assertThat(list).extracting(OutfitResponse::outfitId).containsExactly("o1", "o2");
	}

	@Test
	void delete_existingOutfit_removesRecord() {
		when(repository.findById("o1")).thenReturn(Optional.of(entity("o1")));

		service.delete(USER, "o1");

		verify(repository).deleteById("o1");
	}

	@Test
	void delete_unknownId_throwsOutfitNotFoundAndDeletesNothing() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(OutfitNotFoundException.class)
			.isThrownBy(() -> service.delete(USER, "nope"));
		verify(repository, never()).deleteById(any());
	}

	@Test
	void find_otherUsersOutfit_throwsNotFound() {
		// An outfit owned by userB must be indistinguishable from a missing one to userA:
		// delete throws the same non-enumerating OutfitNotFoundException and removes nothing.
		SavedOutfit foreign = entity("o1");
		foreign.setUserId("userB");
		when(repository.findById("o1")).thenReturn(Optional.of(foreign));

		assertThatExceptionOfType(OutfitNotFoundException.class)
			.isThrownBy(() -> service.delete(USER, "o1"));
		verify(repository, never()).deleteById(any());
	}
}
