package com.ensemble.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link User} DynamoDB bean. The one piece of behavior worth
 * testing (the rest is plain accessors for the bean mapper) is that the
 * {@code username} partition key is normalized — trimmed and lowercased — so that
 * {@code Foo_Bar} and {@code foo_bar} resolve to the same account.
 */
class UserTest {

	@Test
	void usernameIsNormalizedToLowercaseTrimmed() {
		User user = new User();

		user.setUsername("  Foo_Bar  ");

		assertThat(user.getUsername()).isEqualTo("foo_bar");
	}

	@Test
	void normalizeUsername_isNullSafe() {
		assertThat(User.normalizeUsername(null)).isNull();
	}
}
