package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ensemble.config.DynamoDbTableInitializer.GsiSpec;

/**
 * Fast unit tests for {@link GsiSpec}'s compact-constructor validation (spec #15
 * PR review, finding 3). Modeling the GSI as one value means "has a GSI" and
 * "both required fields present" are a single type-checked thing: a partially-null
 * pair — which previously fell through to a silently index-less table — is
 * unrepresentable. Light by design; the table-creation glue itself is exercised by
 * {@code DynamoDbTableInitializerIT} against DynamoDB Local (see docs/TESTING.md).
 */
class DynamoDbTableInitializerTest {

	@Test
	void gsiSpec_rejectsNullPartitionKeyAttribute() {
		assertThatThrownBy(() -> new GsiSpec(null, "userId-index"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void gsiSpec_rejectsBlankPartitionKeyAttribute() {
		assertThatThrownBy(() -> new GsiSpec("   ", "userId-index"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void gsiSpec_rejectsNullIndexName() {
		assertThatThrownBy(() -> new GsiSpec("userId", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void gsiSpec_rejectsBlankIndexName() {
		assertThatThrownBy(() -> new GsiSpec("userId", " "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void gsiSpec_acceptsBothFieldsPresent() {
		assertThatCode(() -> new GsiSpec("userId", "userId-index")).doesNotThrowAnyException();
	}
}
