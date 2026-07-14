package com.ensemble.tagging.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The suggestion DTO must serialize cleanly even when every field is null — a
 * partial/empty suggestion is a normal, renderable state for the review UI, so an
 * all-null {@link TagSuggestion} has to produce valid JSON rather than fail.
 */
class TagSuggestionTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void allNullSuggestion_serializesToValidJson() throws Exception {
		String json = mapper.writeValueAsString(TagSuggestion.empty());

		JsonNode node = mapper.readTree(json);
		assertThat(node.isObject()).isTrue();
		assertThat(node.get("category").isNull()).isTrue();
		assertThat(node.get("formality").isNull()).isTrue();
		assertThat(node.get("descriptors").isNull()).isTrue();
	}

	@Test
	void empty_isTheAllNullSuggestion() {
		assertThat(TagSuggestion.empty())
			.isEqualTo(new TagSuggestion(null, null, null, null, null, null, null));
	}
}
