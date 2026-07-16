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
		item.setPhotoKey(id + ".jpg");
		return item;
	}

	@Test
	void create_generatesIdStoresPhotoAndPersistsItem() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse created = service.create(tags(), new byte[]{1, 2, 3});

		assertThat(created.itemId()).isNotBlank();
		assertThat(created.createdAt()).isNotNull();
		assertThat(created.wornCount()).isZero();
		assertThat(created.category()).isEqualTo("top");
		assertThat(created.photoUrl()).isEqualTo("/api/items/" + created.itemId() + "/photo");

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(photoStorage).save(keyCaptor.capture(), eq(new byte[]{1, 2, 3}));
		assertThat(keyCaptor.getValue()).isEqualTo(created.itemId() + ".jpg");

		ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
		verify(repository).save(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getItemId()).isEqualTo(created.itemId());
		assertThat(itemCaptor.getValue().getPhotoKey()).isEqualTo(created.itemId() + ".jpg");
	}

	@Test
	void create_whenRepositorySaveFails_deletesOrphanedPhoto() {
		when(repository.save(any())).thenThrow(new RuntimeException("dynamo unavailable"));

		assertThatThrownBy(() -> service.create(tags(), new byte[]{1, 2, 3}))
			.isInstanceOf(RuntimeException.class);

		// The photo was written before the failing record save; it must be cleaned up
		// so persistence stays all-or-nothing rather than leaving an orphan file.
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		verify(photoStorage).save(keyCaptor.capture(), any());
		verify(photoStorage).delete(keyCaptor.getValue());
	}

	@Test
	void list_returnsAllItems() {
		when(repository.findAll()).thenReturn(List.of(existing("a"), existing("b")));

		assertThat(service.list()).extracting(ItemResponse::itemId).containsExactly("a", "b");
	}

	@Test
	void get_whenPresent_returnsItem() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));

		assertThat(service.get("x").itemId()).isEqualTo("x");
	}

	@Test
	void get_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.get("nope"));
	}

	@Test
	void updateTags_appliesTagsAndSaves() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse updated = service.updateTags("x", tags());

		assertThat(updated.category()).isEqualTo("top");
		assertThat(updated.warmth()).isEqualTo(2);
		verify(repository).save(any(Item.class));
	}

	@Test
	void updateTags_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.updateTags("nope", tags()));
		verify(repository, never()).save(any());
	}

	@Test
	void delete_removesItemRecordBeforePhoto() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));

		service.delete("x");

		// Record first, then photo: if the photo delete fails the record is already
		// gone (a later get returns 404), rather than leaving a record whose photo is
		// missing (which would 500 on loadPhoto).
		InOrder order = inOrder(repository, photoStorage);
		order.verify(repository).deleteById("x");
		order.verify(photoStorage).delete("x.jpg");
	}

	@Test
	void delete_whenMissing_throwsAndTouchesNothing() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.delete("nope"));
		verify(photoStorage, never()).delete(any());
		verify(repository, never()).deleteById(any());
	}

	@Test
	void loadPhoto_returnsBytesFromStorage() {
		when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
		when(photoStorage.load("x.jpg")).thenReturn(new byte[]{9, 9});

		assertThat(service.loadPhoto("x")).containsExactly(9, 9);
	}

	@Test
	void loadPhoto_whenMissing_throwsItemNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.loadPhoto("nope"));
		verify(photoStorage, never()).load(any());
	}

	@Test
	void markWorn_firstTime_setsCountToOneAndLastWorn() {
		Item item = existing("x");
		item.setWornCount(0);
		item.setLastWorn(null);
		when(repository.findById("x")).thenReturn(Optional.of(item));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse worn = service.markWorn("x");

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

		ItemResponse worn = service.markWorn("x");

		assertThat(worn.wornCount()).isEqualTo(8);
		assertThat(worn.lastWorn()).isNotNull().isAfter(old);
	}

	@Test
	void markWorn_nullCount_treatedAsZero() {
		Item item = existing("x");
		item.setWornCount(null);
		when(repository.findById("x")).thenReturn(Optional.of(item));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		ItemResponse worn = service.markWorn("x");

		assertThat(worn.wornCount()).isEqualTo(1);
	}

	@Test
	void markWorn_unknownId_throwsNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ItemNotFoundException.class)
			.isThrownBy(() -> service.markWorn("nope"));
		verify(repository, never()).save(any());
	}
}
