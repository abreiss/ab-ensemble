package com.ensemble.outfit;

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
import com.ensemble.config.DynamoDbTableInitializer.GsiSpec;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Real DynamoDB Local round-trips for {@link OutfitRepository} via TestContainers.
 * Each test runs against a fresh, uniquely-named dedicated outfits table so cases
 * (including the empty-store case) are fully isolated — no Spring context. Mirrors
 * {@code WardrobeRepositoryIT}, minus the reserved-prefix filtering that only the
 * shared items table needs.
 */
@Testcontainers
class OutfitRepositoryIT {

	private static final int PORT = 8000;

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private OutfitRepository repository;
	private DynamoDbEnhancedClient enhanced;
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

		outfitsTable = "outfits-" + UUID.randomUUID();
		DynamoDbProperties props = new DynamoDbProperties(endpoint, "us-east-1", "unused-items", outfitsTable, "unused-users", true);
		new DynamoDbTableInitializer(client, props).ensureTable(outfitsTable, "outfitId", new GsiSpec("userId", "userId-index"));
		repository = new OutfitRepository(enhanced, props);
	}

	private SavedOutfit sample(String id) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(id);
		// A "sample" outfit represents real, owned data; stamp a default owner so it
		// clears OutfitRepository.save's owner-stamp guard. Tests that need an
		// unowned/legacy row override this to null/blank and seed via saveUnowned(...).
		outfit.setUserId("owner-default");
		outfit.setItemIds(List.of("item-a", "item-b"));
		outfit.setSource("manual");
		outfit.setReason(null);
		outfit.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		return outfit;
	}

	/**
	 * Seeds an outfit straight through the enhanced client, deliberately bypassing
	 * {@link OutfitRepository#save}'s owner-stamp guard — the faithful way to plant a
	 * legacy pre-#15 <em>unowned</em> outfit (data that only ever reaches the table
	 * through a path other than the guarded {@code save()}).
	 */
	private void saveUnowned(SavedOutfit outfit) {
		enhanced.table(outfitsTable, TableSchema.fromBean(SavedOutfit.class)).putItem(outfit);
	}

	@Test
	void save_thenFindById_returnsPersistedOutfitWithAllFields() {
		SavedOutfit saved = sample("out-123");
		saved.setSource("ai");
		saved.setReason("navy blazer anchors the look");

		repository.save(saved);
		SavedOutfit found = repository.findById("out-123").orElseThrow();

		assertThat(found.getOutfitId()).isEqualTo("out-123");
		assertThat(found.getItemIds()).containsExactly("item-a", "item-b");
		assertThat(found.getSource()).isEqualTo("ai");
		assertThat(found.getReason()).isEqualTo("navy blazer anchors the look");
		assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
	}

	@Test
	void findById_whenMissing_returnsEmpty() {
		assertThat(repository.findById("nope")).isEmpty();
	}

	@Test
	void findAll_whenEmpty_returnsEmptyList() {
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void findAll_returnsEverySavedOutfit() {
		repository.save(sample("a"));
		repository.save(sample("b"));
		repository.save(sample("c"));

		List<SavedOutfit> all = repository.findAll();

		assertThat(all).extracting(SavedOutfit::getOutfitId).containsExactlyInAnyOrder("a", "b", "c");
	}

	@Test
	void findByUserId_returnsOnlyThatUsersOutfits() {
		SavedOutfit a1 = sample("a1");
		a1.setUserId("userA");
		SavedOutfit a2 = sample("a2");
		a2.setUserId("userA");
		SavedOutfit b1 = sample("b1");
		b1.setUserId("userB");
		repository.save(a1);
		repository.save(a2);
		repository.save(b1);

		List<SavedOutfit> found = repository.findByUserId("userA");

		assertThat(found).extracting(SavedOutfit::getOutfitId).containsExactlyInAnyOrder("a1", "a2");
	}

	@Test
	void findUnowned_returnsOnlyOutfitsWithNoOrBlankUserId() {
		SavedOutfit orphan = sample("orphan");
		orphan.setUserId(null);
		SavedOutfit blankOwner = sample("blank");
		blankOwner.setUserId("   ");
		SavedOutfit owned = sample("owned");
		owned.setUserId("userA");
		// The unowned rows are seeded via the raw seam (the guarded save() refuses
		// owner-less writes); only the owned row goes through repository.save.
		saveUnowned(orphan);
		saveUnowned(blankOwner);
		repository.save(owned);

		List<SavedOutfit> unowned = repository.findUnowned();

		assertThat(unowned).extracting(SavedOutfit::getOutfitId).containsExactlyInAnyOrder("orphan", "blank");
	}

	@Test
	void deleteById_removesTheOutfit() {
		repository.save(sample("gone"));

		repository.deleteById("gone");

		assertThat(repository.findById("gone")).isEmpty();
	}

	@Test
	void save_thenList_thenDelete_roundTrips() {
		repository.save(sample("rt"));
		assertThat(repository.findAll()).extracting(SavedOutfit::getOutfitId).containsExactly("rt");

		repository.deleteById("rt");

		assertThat(repository.findAll()).isEmpty();
	}
}
