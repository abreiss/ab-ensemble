package com.ensemble.migration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.outfit.OutfitRepository;
import com.ensemble.outfit.SavedOutfit;
import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.Item;
import com.ensemble.wardrobe.WardrobeRepository;

/**
 * Unit tests for {@link UnownedDataPurgeRunner} (spec #15, Unit 5). Plain Mockito —
 * no Spring context — proving the runner's decision branches: no-op when disabled
 * (the critical path that keeps every {@code @SpringBootTest} context safe with no
 * live DynamoDB), no-op when enabled but nothing unowned remains (idempotent re-run),
 * the delete-photo-then-row happy path, and resilience — a failing photo delete must
 * not abort the migration. The real {@code findUnowned} scan is exercised by
 * {@link UnownedDataPurgeRunnerIT} against DynamoDB Local; here the repositories are
 * stubbed so we assert the runner's orchestration alone.
 */
@ExtendWith(MockitoExtension.class)
class UnownedDataPurgeRunnerTest {

	@Mock
	WardrobeRepository wardrobeRepository;

	@Mock
	OutfitRepository outfitRepository;

	@Mock
	PhotoStorage photoStorage;

	@Test
	void purge_whenDisabled_isNoOp() {
		UnownedDataPurgeRunner runner = new UnownedDataPurgeRunner(
			wardrobeRepository, outfitRepository, photoStorage, new MigrationProperties(false));

		runner.run(null);

		// Disabled must return before touching any collaborator — no scan, no delete,
		// so a default @SpringBootTest context (flag off, no live DynamoDB) stays safe.
		verifyNoInteractions(wardrobeRepository, outfitRepository, photoStorage);
	}

	@Test
	void purge_secondRun_isNoOp() {
		// Enabled, but a prior run already cleaned up: the scan finds nothing, so the
		// runner deletes nothing (idempotent).
		when(wardrobeRepository.findUnowned()).thenReturn(List.of());
		when(outfitRepository.findUnowned()).thenReturn(List.of());
		UnownedDataPurgeRunner runner = new UnownedDataPurgeRunner(
			wardrobeRepository, outfitRepository, photoStorage, new MigrationProperties(true));

		runner.run(null);

		verify(wardrobeRepository).findUnowned();
		verify(outfitRepository).findUnowned();
		verify(wardrobeRepository, never()).deleteById(anyString());
		verify(outfitRepository, never()).deleteById(anyString());
		verifyNoInteractions(photoStorage);
	}

	@Test
	void purge_whenEnabled_deletesUnownedItemPhotosThenRows_andUnownedOutfits() {
		Item orphanA = item("orphan-a", "orphan-a.jpg");
		Item orphanB = item("orphan-b", "orphan-b.jpg");
		SavedOutfit orphanOutfit = outfit("orphan-outfit");
		when(wardrobeRepository.findUnowned()).thenReturn(List.of(orphanA, orphanB));
		when(outfitRepository.findUnowned()).thenReturn(List.of(orphanOutfit));
		UnownedDataPurgeRunner runner = new UnownedDataPurgeRunner(
			wardrobeRepository, outfitRepository, photoStorage, new MigrationProperties(true));

		runner.run(null);

		verify(photoStorage).delete("orphan-a.jpg");
		verify(photoStorage).delete("orphan-b.jpg");
		verify(wardrobeRepository).deleteById("orphan-a");
		verify(wardrobeRepository).deleteById("orphan-b");
		verify(outfitRepository).deleteById("orphan-outfit");
	}

	@Test
	void purge_whenPhotoDeleteFails_stillDeletesRowAndContinues() {
		Item orphanA = item("orphan-a", "orphan-a.jpg");
		Item orphanB = item("orphan-b", "orphan-b.jpg");
		when(wardrobeRepository.findUnowned()).thenReturn(List.of(orphanA, orphanB));
		when(outfitRepository.findUnowned()).thenReturn(List.of());
		// The first item's photo delete blows up; the migration must not abort.
		doThrow(new RuntimeException("storage down")).when(photoStorage).delete("orphan-a.jpg");
		UnownedDataPurgeRunner runner = new UnownedDataPurgeRunner(
			wardrobeRepository, outfitRepository, photoStorage, new MigrationProperties(true));

		runner.run(null);

		// Row A is still purged despite its photo-delete failure, and item B is reached.
		verify(wardrobeRepository).deleteById("orphan-a");
		verify(photoStorage).delete("orphan-b.jpg");
		verify(wardrobeRepository).deleteById("orphan-b");
	}

	private static Item item(String id, String photoKey) {
		Item item = new Item();
		item.setItemId(id);
		item.setPhotoKey(photoKey);
		return item;
	}

	private static SavedOutfit outfit(String id) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(id);
		return outfit;
	}
}
