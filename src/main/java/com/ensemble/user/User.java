package com.ensemble.user;

import java.time.Instant;
import java.util.Locale;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * A user account, persisted as a first-class DynamoDB item in its own dedicated
 * users table (issue #14). Mapped by the AWS SDK v2 Enhanced Client: the
 * normalized {@code email} is the partition key, so a conditional
 * {@code attribute_not_exists(email)} put enforces one account per email
 * atomically (see {@link UserRepository}).
 *
 * <p>{@code userId} is a generated UUID — the opaque, durable identity embedded
 * in session tokens and later used to scope wardrobe data (#15). {@code email}
 * is normalized (trimmed + lowercased) on set so {@code Foo@X.com} and
 * {@code foo@x.com} resolve to the same account. A no-arg constructor with
 * getters/setters is required by the bean mapper.
 *
 * <p>{@code passwordHash} holds only the bcrypt hash — never a raw password.
 * There is deliberately no {@code toString()} override: the default identity
 * string never echoes the hash into logs.
 */
@DynamoDbBean
public class User {

	private String email;
	private String userId;
	private String passwordHash;
	private Instant createdAt;

	/** Trims and lowercases an email for use as the key; null-safe (null → null). */
	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	@DynamoDbPartitionKey
	public String getEmail() {
		return email;
	}

	/** Normalizes on set so the stored key and all lookups agree. */
	public void setEmail(String email) {
		this.email = normalizeEmail(email);
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
