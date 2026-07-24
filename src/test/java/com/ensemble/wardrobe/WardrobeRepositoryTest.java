package com.ensemble.wardrobe;

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
 * Fast unit tests for {@link WardrobeRepository#save}'s owner-stamp precondition
 * guard (spec #15 PR review, finding 4). The DynamoDB seam is mocked, so these
 * assert <em>our</em> guard — {@code userId} is the whole security boundary, so no
 * row may be persisted without one — with no TestContainers round-trip: an
 * owner-less write is rejected <em>before</em> {@code putItem}, closing the footgun
 * of a future construction path that forgets {@code setUserId}. Both the null and
 * blank branches are covered (100% branch on the guard).
 */
@ExtendWith(MockitoExtension.class)
class WardrobeRepositoryTest {

	@Mock
	private DynamoDbEnhancedClient enhancedClient;

	@Mock
	private DynamoDbTable<Item> table;

	private WardrobeRepository repository;

	@BeforeEach
	void setUp() {
		when(enhancedClient.<Item>table(any(), any())).thenReturn(table);
		repository = new WardrobeRepository(enhancedClient,
			new DynamoDbProperties("http://local", "us-east-1", "items", "outfits", "users", true));
	}

	@Test
	void save_withNullUserId_throwsAndNeverPuts() {
		Item item = new Item();
		item.setItemId("no-owner");
		// userId deliberately left null

		assertThatThrownBy(() -> repository.save(item)).isInstanceOf(IllegalStateException.class);
		verify(table, never()).putItem(any(Item.class));
	}

	@Test
	void save_withBlankUserId_throwsAndNeverPuts() {
		Item item = new Item();
		item.setItemId("blank-owner");
		item.setUserId("   ");

		assertThatThrownBy(() -> repository.save(item)).isInstanceOf(IllegalStateException.class);
		verify(table, never()).putItem(any(Item.class));
	}

	@Test
	void save_withOwner_putsAndReturnsItem() {
		Item item = new Item();
		item.setItemId("owned");
		item.setUserId("userA");

		Item saved = repository.save(item);

		assertThat(saved).isSameAs(item);
		verify(table).putItem(item);
	}
}
