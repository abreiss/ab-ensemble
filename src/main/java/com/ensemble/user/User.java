package com.ensemble.user;

import java.time.Instant;
import java.util.Locale;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A user account, persisted as a first-class DynamoDB item in its own dedicated
 * users table (issue #14). Mapped by the AWS SDK v2 Enhanced Client: the
 * normalized {@code username} is the partition key, so a conditional
 * {@code attribute_not_exists(username)} put enforces one account per username
 * atomically (see {@link UserRepository}).
 *
 * <p>{@code userId} is a generated UUID — the opaque, durable identity embedded
 * in session tokens and later used to scope wardrobe data (#15). {@code username}
 * is normalized (trimmed + lowercased) on set so {@code Foo_Bar} and
 * {@code foo_bar} resolve to the same account. A no-arg constructor with
 * getters/setters is required by the bean mapper.
 *
 * <p>{@code passwordHash} holds only the bcrypt hash — never a raw password.
 * There is deliberately no {@code toString()} override: the default identity
 * string never echoes the hash into logs.
 */
@DynamoDbBean
public class User {

	private String username;
	private String userId;
	private String passwordHash;
	private Instant createdAt;

	/** Trims and lowercases a username for use as the key; null-safe (null → null). */
	public static String normalizeUsername(String username) {
		return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
	}

	@DynamoDbPartitionKey
	public String getUsername() {
		return username;
	}

	/** Normalizes on set so the stored key and all lookups agree. */
	public void setUsername(String username) {
		this.username = normalizeUsername(username);
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
