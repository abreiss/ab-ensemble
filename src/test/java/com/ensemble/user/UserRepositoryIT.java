package com.ensemble.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ensemble.config.DynamoDbProperties;
import com.ensemble.config.DynamoDbTableInitializer;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Real DynamoDB Local round-trips for {@link UserRepository} via TestContainers.
 * Each test runs against a fresh, uniquely-named users table so cases are fully
 * isolated — no Spring context. Mirrors {@code OutfitRepositoryIT}, and adds the
 * atomic {@code attribute_not_exists(username)} uniqueness guard and the case/space
 * insensitivity that the username partition key requires.
 */
@Testcontainers
class UserRepositoryIT {

	private static final int PORT = 8000;

	@Container
	static final GenericContainer<?> DYNAMODB =
		new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
			.withExposedPorts(PORT);

	private UserRepository repository;

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

		String usersTable = "users-" + UUID.randomUUID();
		DynamoDbProperties props =
			new DynamoDbProperties(endpoint, "us-east-1", "unused-items", "unused-outfits", usersTable, true);
		new DynamoDbTableInitializer(client, props).ensureTable(usersTable, "username");
		repository = new UserRepository(enhanced, props);
	}

	private User sample(String username) {
		User user = new User();
		user.setUsername(username);
		user.setUserId(UUID.randomUUID().toString());
		user.setPasswordHash("$2a$12$abcdefghijklmnopqrstuvHASHPLACEHOLDER0000000000000000000");
		user.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		return user;
	}

	@Test
	void createThenFindByUsername() {
		User user = sample("alice_user");

		repository.create(user);
		User found = repository.findByUsername("alice_user").orElseThrow();

		assertThat(found.getUsername()).isEqualTo("alice_user");
		assertThat(found.getUserId()).isEqualTo(user.getUserId());
		assertThat(found.getPasswordHash()).isEqualTo(user.getPasswordHash());
		assertThat(found.getCreatedAt()).isEqualTo(user.getCreatedAt());
	}

	@Test
	void findByUsername_whenMissing_returnsEmpty() {
		assertThat(repository.findByUsername("nobody_user")).isEmpty();
	}

	@Test
	void create_duplicateUsername_throwsDuplicateUsernameException() {
		User first = sample("dup_user");
		repository.create(first);

		// Same normalized username, different userId → the conditional put must fail.
		assertThatThrownBy(() -> repository.create(sample("dup_user")))
			.isInstanceOf(DuplicateUsernameException.class);

		// And it must not have overwritten the first row — the original userId still stands.
		assertThat(repository.findByUsername("dup_user").orElseThrow().getUserId())
			.isEqualTo(first.getUserId());
	}

	@Test
	void findByUsername_isCaseAndSpaceInsensitive() {
		User user = sample("mixed_name");
		repository.create(user);

		assertThat(repository.findByUsername("  Mixed_Name  ")).isPresent();
	}

	@Test
	void findByUserId_returnsUser() {
		User user = sample("byid_user");
		repository.create(user);

		User found = repository.findByUserId(user.getUserId()).orElseThrow();

		assertThat(found.getUsername()).isEqualTo("byid_user");
		assertThat(found.getUserId()).isEqualTo(user.getUserId());
	}

	@Test
	void findByUserId_whenMissing_returnsEmpty() {
		repository.create(sample("other_user"));

		assertThat(repository.findByUserId("no-such-user-id")).isEmpty();
	}

	@Test
	void findByUserId_whenNull_returnsEmpty() {
		// Defensive null guard — never scans, returns empty (covers the null branch).
		assertThat(repository.findByUserId(null)).isEmpty();
	}
}
