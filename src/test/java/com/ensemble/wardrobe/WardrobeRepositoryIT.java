package com.ensemble.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ensemble.config.DynamoDbProperties;
import com.ensemble.config.DynamoDbTableInitializer;
import com.ensemble.storage.PhotoStorage;
import com.ensemble.wardrobe.dto.ItemResponse;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Real DynamoDB Local round-trips for {@link WardrobeRepository} via
 * TestContainers. Each test runs against a fresh, uniquely-named table so cases
 * (including the empty-wardrobe case) are fully isolated — no Spring context.
 */
@Testcontainers
class WardrobeRepositoryIT {

	private static final int PORT = 8000;

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private WardrobeRepository repository;

	@BeforeEach
	void setUp() {
		String endpoint = "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(PORT);
		DynamoDbClient client = DynamoDbClient.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create("local", "local")))
			.build();
		DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
			.dynamoDbClient(client)
			.build();

		String tableName = "items-" + UUID.randomUUID();
		DynamoDbProperties props = new DynamoDbProperties(endpoint, "us-east-1", tableName, true);
		new DynamoDbTableInitializer(client, props).ensureTable();
		repository = new WardrobeRepository(enhanced, props);
	}

	private Item sampleItem(String id) {
		Item item = new Item();
		item.setItemId(id);
		item.setCategory("top");
		item.setPrimaryColor("navy");
		item.setSecondaryColor("white");
		item.setFormality(3);
		item.setPattern("striped");
		item.setWarmth(2);
		item.setDescriptors(List.of("cotton", "long-sleeve"));
		item.setPhotoKey(id + ".jpg");
		item.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		item.setLastWorn(null);
		item.setWornCount(0);
		return item;
	}

	@Test
	void save_thenFindById_returnsPersistedItemWithAllFields() {
		Item saved = sampleItem("abc-123");

		repository.save(saved);
		Item found = repository.findById("abc-123").orElseThrow();

		assertThat(found.getItemId()).isEqualTo("abc-123");
		assertThat(found.getCategory()).isEqualTo("top");
		assertThat(found.getPrimaryColor()).isEqualTo("navy");
		assertThat(found.getSecondaryColor()).isEqualTo("white");
		assertThat(found.getFormality()).isEqualTo(3);
		assertThat(found.getPattern()).isEqualTo("striped");
		assertThat(found.getWarmth()).isEqualTo(2);
		assertThat(found.getDescriptors()).containsExactly("cotton", "long-sleeve");
		assertThat(found.getPhotoKey()).isEqualTo("abc-123.jpg");
		assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(found.getWornCount()).isEqualTo(0);
	}

	@Test
	void findById_whenMissing_returnsEmpty() {
		assertThat(repository.findById("nope")).isEmpty();
	}

	@Test
	void findAll_whenEmptyWardrobe_returnsEmptyList() {
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void findAll_returnsEverySavedItem() {
		repository.save(sampleItem("a"));
		repository.save(sampleItem("b"));
		repository.save(sampleItem("c"));

		List<Item> all = repository.findAll();

		assertThat(all).extracting(Item::getItemId).containsExactlyInAnyOrder("a", "b", "c");
	}

	@Test
	void save_existingId_replacesTheItem() {
		repository.save(sampleItem("x"));

		Item update = sampleItem("x");
		update.setPrimaryColor("black");
		update.setDescriptors(List.of("wool"));
		repository.save(update);

		Item found = repository.findById("x").orElseThrow();
		assertThat(found.getPrimaryColor()).isEqualTo("black");
		assertThat(found.getDescriptors()).containsExactly("wool");
		assertThat(repository.findAll()).hasSize(1);
	}

	@Test
	void deleteById_removesTheItem() {
		repository.save(sampleItem("gone"));

		repository.deleteById("gone");

		assertThat(repository.findById("gone")).isEmpty();
	}

	@Test
	void markWorn_roundTrip_persistsIncrementAndLastWornLeavingTagsUntouched() {
		// A no-op storage: markWorn never touches photos, so this exercises only the
		// wear-history read-modify-write through the real repository/table.
		PhotoStorage noPhotos = new PhotoStorage() {
			@Override
			public void save(String key, byte[] imageBytes) {
			}

			@Override
			public byte[] load(String key) {
				return new byte[0];
			}

			@Override
			public void delete(String key) {
			}
		};
		WardrobeService service = new WardrobeService(repository, noPhotos);
		Item seed = sampleItem("worn-1");
		Instant createdAt = seed.getCreatedAt();
		repository.save(seed);

		ItemResponse worn = service.markWorn("worn-1");

		assertThat(worn.wornCount()).isEqualTo(1);
		assertThat(worn.lastWorn()).isNotNull();
		// Reload from the table: the change is persisted and tags/createdAt are untouched.
		Item reloaded = repository.findById("worn-1").orElseThrow();
		assertThat(reloaded.getWornCount()).isEqualTo(1);
		assertThat(reloaded.getLastWorn()).isNotNull();
		assertThat(reloaded.getCategory()).isEqualTo("top");
		assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
	}
}
