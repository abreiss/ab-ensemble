package com.ensemble.outfit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ensemble.outfit.SavedOutfit;

class OutfitMapperTest {

	private SavedOutfit outfit(String id) {
		SavedOutfit outfit = new SavedOutfit();
		outfit.setOutfitId(id);
		outfit.setItemIds(List.of("item-a", "item-b"));
		outfit.setSource("ai");
		outfit.setReason("navy blazer anchors it");
		outfit.setCreatedAt(Instant.parse("2026-07-13T00:00:00Z"));
		return outfit;
	}

	@Test
	void toResponse_mapsAllFields() {
		OutfitResponse response = OutfitMapper.toResponse(outfit("out-1"));

		assertThat(response.outfitId()).isEqualTo("out-1");
		assertThat(response.itemIds()).containsExactly("item-a", "item-b");
		assertThat(response.source()).isEqualTo("ai");
		assertThat(response.reason()).isEqualTo("navy blazer anchors it");
		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
	}

	@Test
	void toEntity_copiesRequestFieldsWithServerOwnedIdAndCreatedAt() {
		SaveOutfitRequest request = new SaveOutfitRequest(List.of("item-a"), "manual", null);
		Instant createdAt = Instant.parse("2026-07-13T00:00:00Z");

		SavedOutfit entity = OutfitMapper.toEntity(request, "server-id", createdAt);

		assertThat(entity.getOutfitId()).isEqualTo("server-id");
		assertThat(entity.getItemIds()).containsExactly("item-a");
		assertThat(entity.getSource()).isEqualTo("manual");
		assertThat(entity.getReason()).isNull();
		assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
	}
}
