package com.ensemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ensemble.config.DynamoDbProperties;
import com.ensemble.config.DynamoDbTableInitializer;
import com.ensemble.config.DynamoDbTableInitializer.GsiSpec;
import com.ensemble.outfit.OutfitRepository;
import com.ensemble.outfit.SavedOutfit;
import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.Item;
import com.ensemble.wardrobe.WardrobeRepository;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Real DynamoDB Local round-trips for {@link UnownedDataPurgeRunner} via TestContainers
 * (spec #15, Unit 5). Each test runs against fresh, uniquely-named items + outfits tables
 * so cases are fully isolated — no Spring context. Proves the end-to-end purge against the
 * real {@code findUnowned} scan + delete path: only rows with no {@code userId} (and their
 * photos) are removed, while owned rows, their photos, and the reserved {@code usage#<date>}
 * counter row survive; and the whole thing is a no-op when the flag is off.
 */
@Testcontainers
class UnownedDataPurgeRunnerIT {

	private static final int PORT = 8000;

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private WardrobeRepository wardrobeRepository;
	private OutfitRepository outfitRepository;
	private DynamoDbEnhancedClient enhanced;
	private String itemsTable;
	private String outfitsTable;

	@BeforeEach
	void setUp() {
		String endpoint = "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(PORT);
		DynamoDbClient client = DynamoDbClient.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create("local", "local")))
			.build();
		enhanced = DynamoDbEnhancedClient.builder()
			.dynamoDbClient(client)
			.build();

		itemsTable = "items-" + UUID.randomUUID();
		outfitsTable = "outfits-" + UUID.randomUUID();
		DynamoDbProperties props =
			new DynamoDbProperties(endpoint, "us-east-1", itemsTable, outfitsTable, "unused-users", true);
		DynamoDbTableInitializer init = new DynamoDbTableInitializer(client, props);
		init.ensureTable(itemsTable, "itemId", new GsiSpec("userId", "userId-index"));
		init.ensureTable(outfitsTable, "outfitId", new GsiSpec("userId", "userId-index"));
		wardrobeRepository = new WardrobeRepository(enhanced, props);
		outfitRepository = new OutfitRepository(enhanced, props);
	}

	/**
	 * Seeds an item/outfit straight through the enhanced client, deliberately
	 * bypassing the repositories' owner-stamp guard (spec #15). This is the faithful
	 * way to plant the legacy pre-#15 <em>unowned</em> rows (and the reserved
	 * {@code usage#<date>} counter row) the purge exists to act on — data that only
	 * ever reaches the table through a path other than the guarded {@code save()}.
	 */
	private void saveUnowned(Item item) {
		enhanced.table(itemsTable, TableSchema.fromBean(Item.class)).putItem(item);
	}

	private void saveUnowned(SavedOutfit outfit) {
		enhanced.table(outfitsTable, TableSchema.fromBean(SavedOutfit.class)).putItem(outfit);
	}

	@Test
	void purge_whenEnabled_removesOnlyUnownedRowsAndTheirPhotos_leavingOwnedAndUsageRows() {
		Map<String, byte[]> photos = new HashMap<>();
		PhotoStorage photoStorage = inMemory(photos);

		// Unowned item with a stored photo (a legacy pre-ownership row). Seeded via the
		// raw seam since the guarded save() now refuses owner-less writes.
		saveUnowned(item("orphan-1", null, "orphan-1.jpg"));
		photos.put("orphan-1.jpg", new byte[] {1, 2, 3});
		// Unowned item whose photo is ALREADY missing — resilience: the run must not abort.
		saveUnowned(item("orphan-2", null, "orphan-2.jpg"));
		// Owned item + its photo — must survive (goes through the guarded save()).
		wardrobeRepository.save(item("owned-1", "userA", "userA/owned-1.jpg"));
		photos.put("userA/owned-1.jpg", new byte[] {9});
		// Reserved usage#<date> daily-cap counter row (no userId) — must survive.
		Item usageRow = new Item();
		usageRow.setItemId("usage#2026-07-16");
		saveUnowned(usageRow);
		// Unowned + owned outfits.
		saveUnowned(outfit("orphan-outfit", null));
		outfitRepository.save(outfit("owned-outfit", "userA"));

		newRunner(photoStorage, true).run(null);

		// Unowned rows and the stored orphan photo are gone.
		assertThat(wardrobeRepository.findById("orphan-1")).isEmpty();
		assertThat(wardrobeRepository.findById("orphan-2")).isEmpty();
		assertThat(photos).doesNotContainKey("orphan-1.jpg");
		assertThat(outfitRepository.findById("orphan-outfit")).isEmpty();
		// Owned rows, their photos, and the usage counter survive untouched.
		assertThat(wardrobeRepository.findById("owned-1")).isPresent();
		assertThat(photos).containsKey("userA/owned-1.jpg");
		assertThat(wardrobeRepository.findById("usage#2026-07-16")).isPresent();
		assertThat(outfitRepository.findById("owned-outfit")).isPresent();
	}

	@Test
	void purge_whenDisabled_leavesEverythingUntouched() {
		Map<String, byte[]> photos = new HashMap<>();
		PhotoStorage photoStorage = inMemory(photos);
		// Seeded via the raw seam (guarded save() refuses owner-less writes).
		saveUnowned(item("orphan-1", null, "orphan-1.jpg"));
		photos.put("orphan-1.jpg", new byte[] {1});
		saveUnowned(outfit("orphan-outfit", null));

		newRunner(photoStorage, false).run(null);

		assertThat(wardrobeRepository.findById("orphan-1")).isPresent();
		assertThat(photos).containsKey("orphan-1.jpg");
		assertThat(outfitRepository.findById("orphan-outfit")).isPresent();
	}

	private UnownedDataPurgeRunner newRunner(PhotoStorage photoStorage, boolean enabled) {
		return new UnownedDataPurgeRunner(
			wardrobeRepository, outfitRepository, photoStorage, new MigrationProperties(enabled));
	}

	private static Item item(String id, String userId, String photoKey) {
		Item item = new Item();
		item.setItemId(id);
		item.setUserId(userId);
		item.setPhotoKey(photoKey);
		return item;
	}

	private static SavedOutfit outfit(String id, String userId) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(id);
		outfit.setUserId(userId);
		return outfit;
	}

	/** An in-memory {@link PhotoStorage}; the purge only exercises {@code delete}. */
	private static PhotoStorage inMemory(Map<String, byte[]> photos) {
		return new PhotoStorage() {
			@Override
			public void save(String key, byte[] imageBytes) {
				photos.put(key, imageBytes);
			}

			@Override
			public byte[] load(String key) {
				throw new UnsupportedOperationException("not used by the purge");
			}

			@Override
			public void delete(String key) {
				photos.remove(key);
			}
		};
	}
}
