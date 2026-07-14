package com.ensemble.wardrobe.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ensemble.wardrobe.Item;

class ItemMapperTest {

	private Item item(String id) {
		Item item = new Item();
		item.setItemId(id);
		item.setCategory("top");
		item.setPrimaryColor("navy");
		item.setSecondaryColor("white");
		item.setFormality(3);
		item.setPattern("striped");
		item.setWarmth(2);
		item.setDescriptors(List.of("cotton"));
		item.setPhotoKey(id + ".jpg");
		item.setCreatedAt(Instant.parse("2026-07-13T00:00:00Z"));
		item.setLastWorn(null);
		item.setWornCount(0);
		return item;
	}

	@Test
	void toResponse_mapsAllFieldsAndBuildsPhotoUrl() {
		ItemResponse response = ItemMapper.toResponse(item("id-1"));

		assertThat(response.itemId()).isEqualTo("id-1");
		assertThat(response.category()).isEqualTo("top");
		assertThat(response.primaryColor()).isEqualTo("navy");
		assertThat(response.secondaryColor()).isEqualTo("white");
		assertThat(response.formality()).isEqualTo(3);
		assertThat(response.pattern()).isEqualTo("striped");
		assertThat(response.warmth()).isEqualTo(2);
		assertThat(response.descriptors()).containsExactly("cotton");
		assertThat(response.photoUrl()).isEqualTo("/api/items/id-1/photo");
		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
		assertThat(response.wornCount()).isEqualTo(0);
	}

	@Test
	void applyTags_copiesTagFieldsOntoItem() {
		Item target = new Item();
		TagRequest tags = new TagRequest("bottom", "black", "grey", 4, "solid", 3, List.of("wool", "warm"));

		ItemMapper.applyTags(target, tags);

		assertThat(target.getCategory()).isEqualTo("bottom");
		assertThat(target.getPrimaryColor()).isEqualTo("black");
		assertThat(target.getSecondaryColor()).isEqualTo("grey");
		assertThat(target.getFormality()).isEqualTo(4);
		assertThat(target.getPattern()).isEqualTo("solid");
		assertThat(target.getWarmth()).isEqualTo(3);
		assertThat(target.getDescriptors()).containsExactly("wool", "warm");
	}
}
