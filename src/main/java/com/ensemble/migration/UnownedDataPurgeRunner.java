package com.ensemble.migration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ensemble.outfit.OutfitRepository;
import com.ensemble.outfit.SavedOutfit;
import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.Item;
import com.ensemble.wardrobe.WardrobeRepository;

/**
 * One-time, opt-in cleanup of pre-existing "unowned" data — {@link Item}s and
 * {@link SavedOutfit}s written before per-user ownership (spec #15) existed, so they
 * carry no {@code userId}. Gated by {@code ensemble.migration.purge-unowned} (default
 * off via {@link MigrationProperties}), so it is a no-op on every ordinary startup —
 * including every {@code @SpringBootTest} context, which has no live DynamoDB. Like
 * {@code SeedAccountRunner}, it must return before touching any collaborator when
 * disabled.
 *
 * <p>Ordered to run <em>after</em> {@link com.ensemble.config.DynamoDbTableInitializer}
 * (which creates the local tables) and {@code SeedAccountRunner}. When enabled it deletes
 * each unowned item's photo then its row, and each unowned outfit's row; it never touches
 * owned rows or the reserved {@code usage#<date>} daily-cap counter rows (both excluded by
 * {@link WardrobeRepository#findUnowned()} / {@link OutfitRepository#findUnowned()}). A
 * photo-delete failure is logged and the row is still purged, so one bad row cannot abort
 * the run; the run is idempotent, so re-running finishes any leftover work.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 40)
public class UnownedDataPurgeRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UnownedDataPurgeRunner.class);

	private final WardrobeRepository wardrobeRepository;
	private final OutfitRepository outfitRepository;
	private final PhotoStorage photoStorage;
	private final MigrationProperties props;

	public UnownedDataPurgeRunner(WardrobeRepository wardrobeRepository, OutfitRepository outfitRepository,
			PhotoStorage photoStorage, MigrationProperties props) {
		this.wardrobeRepository = wardrobeRepository;
		this.outfitRepository = outfitRepository;
		this.photoStorage = photoStorage;
		this.props = props;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!props.purgeUnowned()) {
			log.debug("Unowned-data purge not enabled (ensemble.migration.purge-unowned=false); skipping");
			return;
		}
		List<Item> unownedItems = wardrobeRepository.findUnowned();
		for (Item item : unownedItems) {
			try {
				photoStorage.delete(item.getPhotoKey());
			} catch (RuntimeException e) {
				// Never let one unreadable/absent photo abort the migration — purge the row
				// anyway and move on; the run is idempotent, so a re-run is always safe.
				log.warn("Failed to delete photo {} for unowned item {}; deleting row anyway",
					item.getPhotoKey(), item.getItemId(), e);
			}
			wardrobeRepository.deleteById(item.getItemId());
		}
		List<SavedOutfit> unownedOutfits = outfitRepository.findUnowned();
		for (SavedOutfit outfit : unownedOutfits) {
			outfitRepository.deleteById(outfit.getOutfitId());
		}
		log.info("Unowned-data purge complete: removed {} unowned items and {} unowned outfits",
			unownedItems.size(), unownedOutfits.size());
	}
}
