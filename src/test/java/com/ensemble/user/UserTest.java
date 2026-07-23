package com.ensemble.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link User} DynamoDB bean. The one piece of behavior worth
 * testing (the rest is plain accessors for the bean mapper) is that the
 * {@code email} partition key is normalized — trimmed and lowercased — so that
 * {@code Foo@X.com} and {@code foo@x.com} resolve to the same account.
 */
class UserTest {

	@Test
	void emailIsNormalizedToLowercaseTrimmed() {
		User user = new User();

		user.setEmail("  Foo@X.com  ");

		assertThat(user.getEmail()).isEqualTo("foo@x.com");
	}

	@Test
	void normalizeEmail_isNullSafe() {
		assertThat(User.normalizeEmail(null)).isNull();
	}
}
