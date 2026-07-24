package com.ensemble.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

@ExtendWith(MockitoExtension.class)
class WardrobeServiceTest {

	/** The authenticated caller every id-based operation is scoped to (spec #15). */
	private static final String USER = "userA";

	@Mock
	WardrobeRepository repository;

	@Mock
	PhotoStorage photoStorage;

	@InjectMocks
	WardrobeService service;

	private TagRequest tags() {
		return new TagRequest("top", "navy", "white", 3, "striped", 2, List.of("cotton"));
	}

	private Item existing(String id) {
		Item item = new Item();
		item.setItemId(id);
		item.setUserId(USER);
		item.setPhotoKey(USER + "/" + id + ".jpg");
		return item;
	}

	@Test
	void create_generatesIdStoresPhotoAndPersistsItem() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse created = service.create(USER, tags(), new byte[]{1, 2, 3});

		assertThat(created.itemId()).isNotBlank();
		assertThat(created.createdAt()).isNotNull();
		assertThat(created.wornCount()).isZero();
		// "top" is normalized to the canonical taxonomy value "Top" at the
		// ItemMapper.applyTags choke point create() calls — the save-path
		// normalization guarantee covers create, not just updateTags.
		assertThat(created.category()).isEqualTo("Top");
		assertThat(created.photoUrl()).isEqualTo("/api/items/" + created.itemId() + "/photo");

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(photoStorage).save(keyCaptor.capture(), eq(new byte[]{1, 2, 3}));
		assertThat(keyCaptor.getValue()).isEqualTo(USER + "/" + created.itemId() + ".jpg");

		ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
		verify(repository).save(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getItemId()).isEqualTo(created.itemId());
		assertThat(itemCaptor.getValue().getPhotoKey()).isEqualTo(USER + "/" + created.itemId() + ".jpg");
	}

	@Test
	void create_namespacesPhotoKeyPerUser() {
		// The photo key is namespaced under the owner (<userId>/<itemId>.jpg) so one user's
		// photos live under a per-user prefix and can never collide with or be reached under
		// another user's id. Capture both the storage key and the persisted key.
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse created = service.create(USER, tags(), new byte[]{1, 2, 3});

		String expectedKey = USER + "/" + created.itemId() + ".jpg";
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(photoStorage).save(keyCaptor.capture(), any());
		assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);

		ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
		verify(repository).save(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getPhotoKey()).isEqualTo(expectedKey);
	}

	@Test
	void create_stampsCallerUserId() {
		// The owner FR: a created item must carry the caller's userId so it is only ever
		// returned to that user. Capture the persisted entity and assert the stamp directly —
		// a scoped read test alone would still pass if create() forgot to set the owner.
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.create(USER, tags(), new byte[]{1, 2, 3});

		ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
		verify(repository).save(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getUserId()).isEqualTo(USER);
	}

	@Test
	void create_whenRepositorySaveFails_deletesOrphanedPhoto() {
		when(repository.save(any())).thenThrow(new RuntimeException("dynamo unavailable"));

		assertThatThrownBy(() -> service.create(USER, tags(), new byte[]{1, 2, 3}))
			.isInstanceOf(RuntimeException.class);

		// The photo was written before the failing record save; it must be cleaned up
		// so persistence stays all-or-nothing rather than leaving an orphan file.
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(photoStorage).save(keyCaptor.capture(), any());
		verify(photoStorage).delete(keyCaptor.getValue());
	}

	@Test
	void list_returnsOnlyCallersItems() {
		// The scoped list delegates to the userId GSI query, so it can only ever return the
		// caller's rows — no full-table scan, no other user's items.
		when(repository.findByUserId(USER)).thenReturn(List.of(existing("a"), existing("b")));

		assertThat(service.list(USER)).extracting(ItemResponse::itemId).containsExactly("a", "b");
	}

	@Test
	void get_whenPresent_returnsItem() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));

		assertThat(service.get(USER, "x").itemId()).isEqualTo("x");
	}

	@Test
	void get_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.get(USER, "nope"));
	}

	@Test
	void find_otherUsersItem_throwsNotFound() {
		// An item owned by userB must be indistinguishable from a missing item to userA:
		// the ownership choke point throws the same non-enumerating ItemNotFoundException,
		// never that item's contents.
		Item foreign = existing("x");
		foreign.setUserId("userB");
		when(repository.findById("x")).thenReturn(Optional.of(foreign));

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.get(USER, "x"));
	}

	@Test
	void updateTags_appliesTagsAndSaves() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse updated = service.updateTags(USER, "x", tags());

		// Same choke point as create(): "top" normalizes to "Top".
		assertThat(updated.category()).isEqualTo("Top");
		assertThat(updated.warmth()).isEqualTo(2);
		verify(repository).save(any(Item.class));
	}

	@Test
	void updateTags_legacyCategory_persistsNormalizedTaxonomyValue() {
		// Explicit choke-point coverage for updateTags with an off-taxonomy input,
		// mirroring the create()-path assertion above (spec Unit 1, Success Metric 5).
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		TagRequest legacy = new TagRequest("chinos", null, null, null, null, null, null);

		ItemResponse updated = service.updateTags(USER, "x", legacy);

		assertThat(updated.category()).isEqualTo("Bottom");
	}

	@Test
	void updateTags_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.updateTags(USER, "nope", tags()));
		verify(repository, never()).save(any());
	}

	@Test
	void delete_removesItemRecordBeforePhoto() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));

		service.delete(USER, "x");

		// Record first, then photo: if the photo delete fails the record is already
		// gone (a later get returns 404), rather than leaving a record whose photo is
		// missing (which would 500 on loadPhoto).
		InOrder order = inOrder(repository, photoStorage);
		order.verify(repository).deleteById("x");
		order.verify(photoStorage).delete(USER + "/x.jpg");
	}

	@Test
	void delete_whenMissing_throwsAndTouchesNothing() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.delete(USER, "nope"));
		verify(photoStorage, never()).delete(any());
		verify(repository, never()).deleteById(any());
	}

	@Test
	void loadPhoto_returnsBytesFromStorage() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
		when(photoStorage.load(USER + "/x.jpg")).thenReturn(new byte[]{9, 9});

		assertThat(service.loadPhoto(USER, "x")).containsExactly(9, 9);
	}

	@Test
	void loadPhoto_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.loadPhoto(USER, "nope"));
		verify(photoStorage, never()).load(any());
	}

	@Test
	void markWorn_firstTime_setsCountToOneAndLastWorn() {
		Item item = existing("x");
		item.setWornCount(0);
		item.setLastWorn(null);
		when(repository.findById("x")).thenReturn(Optional.of(item));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse worn = service.markWorn(USER, "x");

		assertThat(worn.wornCount()).isEqualTo(1);
		assertThat(worn.lastWorn()).isNotNull();
		// The mutation is persisted, not just returned.
		ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getWornCount()).isEqualTo(1);
		assertThat(saved.getValue().getLastWorn()).isNotNull();
	}

	@Test
	void markWorn_existingCount_incrementsAndUpdatesLastWorn() {
		Item item = existing("x");
		item.setWornCount(7);
		Instant old = Instant.parse("2020-01-01T00:00:00Z");
		item.setLastWorn(old);
		when(repository.findById("x")).thenReturn(Optional.of(item));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse worn = service.markWorn(USER, "x");

		assertThat(worn.wornCount()).isEqualTo(8);
		assertThat(worn.lastWorn()).isNotNull().isAfter(old);
	}

	@Test
	void markWorn_nullCount_treatedAsZero() {
		Item item = existing("x");
		item.setWornCount(null);
		when(repository.findById("x")).thenReturn(Optional.of(item));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse worn = service.markWorn(USER, "x");

		assertThat(worn.wornCount()).isEqualTo(1);
	}

	@Test
	void markWorn_unknownId_throwsNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.markWorn(USER, "nope"));
		verify(repository, never()).save(any());
	}
}
