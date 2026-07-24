package com.ensemble.user;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Persists {@link User} accounts via the DynamoDB Enhanced Client against the
 * dedicated users table. Thin by design: no relationships, no cascades — keyed
 * on the normalized {@code username} partition key.
 *
 * <p>{@link #create} is a <em>conditional</em> put ({@code attribute_not_exists(username)})
 * so registering an already-taken username fails atomically at the datastore rather
 * than via a read-then-write race, and surfaces as {@link DuplicateUsernameException}.
 * {@link #findByUserId} is a full scan filtered in memory — a demo-scale approach —
 * because the table is username-keyed with no {@code userId} GSI (see the {@code /api/me}
 * note in the task list; a GSI is the scale path if frequent userId lookups ever appear).
 */
@Repository
public class UserRepository {

	private final DynamoDbTable<User> table;

	public UserRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbProperties props) {
		this.table = enhancedClient.table(props.usersTableName(), TableSchema.fromBean(User.class));
	}

	/**
	 * Creates the account, failing atomically if its (normalized) username already
	 * exists — never a silent overwrite.
	 *
	 * @throws DuplicateUsernameException if an account with that username is already registered
	 */
	public void create(User user) {
		try {
			table.putItem(PutItemEnhancedRequest.builder(User.class)
				.item(user)
				.conditionExpression(Expression.builder()
					.expression("attribute_not_exists(username)")
					.build())
				.build());
		} catch (ConditionalCheckFailedException e) {
			throw new DuplicateUsernameException("username already registered");
		}
	}

	/** Returns the account for the normalized username, or empty if none exists. */
	public Optional<User> findByUsername(String username) {
		String key = User.normalizeUsername(username);
		return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(key))));
	}

	/**
	 * Returns the account with the given {@code userId}, or empty if none exists.
	 * A full scan filtered in memory (demo scale, ~1–20 users); the table has no
	 * userId GSI.
	 */
	public Optional<User> findByUserId(String userId) {
		if (userId == null) {
			return Optional.empty();
		}
		return table.scan().items().stream()
			.filter(u -> userId.equals(u.getUserId()))
			.findFirst();
	}
}
