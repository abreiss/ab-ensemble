package com.ensemble.outfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.config.DynamoDbProperties;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

/**
 * Fast unit tests for {@link OutfitRepository#save}'s owner-stamp precondition
 * guard (spec #15 PR review, finding 4). Mirrors {@code WardrobeRepositoryTest}:
 * the DynamoDB seam is mocked, and an owner-less save is rejected <em>before</em>
 * {@code putItem}. Both the null and blank {@code userId} branches are covered.
 */
@ExtendWith(MockitoExtension.class)
class OutfitRepositoryTest {

	@Mock
	private DynamoDbEnhancedClient enhancedClient;

	@Mock
	private DynamoDbTable<SavedOutfit> table;

	private OutfitRepository repository;

	@BeforeEach
	void setUp() {
		when(enhancedClient.<SavedOutfit>table(any(), any())).thenReturn(table);
		repository = new OutfitRepository(enhancedClient,
			new DynamoDbProperties("http://local", "us-east-1", "items", "outfits", "users", true));
	}

	@Test
	void save_withNullUserId_throwsAndNeverPuts() {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId("no-owner");
		// userId deliberately left null

		assertThatThrownBy(() -> repository.save(outfit)).isInstanceOf(IllegalStateException.class);
		verify(table, never()).putItem(any(SavedOutfit.class));
	}

	@Test
	void save_withBlankUserId_throwsAndNeverPuts() {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId("blank-owner");
		outfit.setUserId("   ");

		assertThatThrownBy(() -> repository.save(outfit)).isInstanceOf(IllegalStateException.class);
		verify(table, never()).putItem(any(SavedOutfit.class));
	}

	@Test
	void save_withOwner_putsAndReturnsOutfit() {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId("owned");
		outfit.setUserId("userA");

		SavedOutfit saved = repository.save(outfit);

		assertThat(saved).isSameAs(outfit);
		verify(table).putItem(outfit);
	}
}
