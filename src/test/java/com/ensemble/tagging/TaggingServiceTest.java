package com.ensemble.tagging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ensemble.storage.ImageProcessor;
import com.ensemble.storage.InvalidImageException;
import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Unit tests for the vision-JSON→tag mapping and the graceful fallback — the two
 * paths that require 100% branch coverage. The vision seam and the image guard are
 * both <strong>mocked</strong>, so no key and no network call are involved.
 */
class TaggingServiceTest {

	private static final byte[] PHOTO = {1, 2, 3};
	private static final byte[] RESIZED = {4, 5};

	private final VisionModelClient vision = mock(VisionModelClient.class);
	private final ImageProcessor imageProcessor = mock(ImageProcessor.class);
	private final TaggingService service = new TaggingService(vision, imageProcessor);

	/** Stubs the image guard to succeed and the seam to return {@code json}, then tags. */
	private TagSuggestion suggestWithModelJson(String json) {
		when(imageProcessor.toResizedJpeg(PHOTO)).thenReturn(RESIZED);
		when(vision.extractTagsJson(RESIZED)).thenReturn(json);
		return service.suggest(PHOTO);
	}

	// --- Mapping (FR1.4) ---

	@Test
	void validResponse_mapsSixScalarsPlusDescriptors() {
		TagSuggestion out = suggestWithModelJson("""
			{"category":"top","primaryColor":"navy","secondaryColor":"white",
			 "formality":3,"pattern":"striped","warmth":2,
			 "descriptors":["cotton","slim"]}""");

		assertThat(out.category()).isEqualTo("Top");
		assertThat(out.primaryColor()).isEqualTo("navy");
		assertThat(out.secondaryColor()).isEqualTo("white");
		assertThat(out.formality()).isEqualTo(3);
		assertThat(out.pattern()).isEqualTo("striped");
		assertThat(out.warmth()).isEqualTo(2);
		assertThat(out.descriptors()).containsExactly("cotton", "slim");
	}

	@Test
	void missingScalarField_isLeftNull_othersMapped() {
		// primaryColor absent, formality absent — present fields still map.
		TagSuggestion out = suggestWithModelJson(
			"{\"category\":\"shoes\",\"warmth\":1}");

		assertThat(out.category()).isEqualTo("Shoes");
		assertThat(out.primaryColor()).isNull();
		assertThat(out.formality()).isNull();
		assertThat(out.warmth()).isEqualTo(1);
	}

	@Test
	void nonTextualScalar_isLeftNull() {
		// category arrives as a number, not a string — treated as unknown.
		TagSuggestion out = suggestWithModelJson("{\"category\":42}");

		assertThat(out.category()).isNull();
	}

	@Test
	void offTaxonomyCategory_isNormalizedToATaxonomyValue() {
		// The model's enum hint is advisory only — an off-taxonomy string must still
		// be normalized before it reaches the suggestion (FR 1.5, vision half).
		TagSuggestion out = suggestWithModelJson("{\"category\":\"sweatshirt\"}");

		assertThat(out.category()).isEqualTo("Top");
	}

	@Test
	void canonicalTaxonomyCategory_isUnchanged() {
		TagSuggestion out = suggestWithModelJson("{\"category\":\"Jewelry\"}");

		assertThat(out.category()).isEqualTo("Jewelry");
	}

	@Test
	void missingDescriptors_isNull_and_nonArrayDescriptors_isNull() {
		assertThat(suggestWithModelJson("{\"category\":\"top\"}").descriptors()).isNull();
		assertThat(suggestWithModelJson("{\"descriptors\":\"cotton\"}").descriptors()).isNull();
	}

	@Test
	void descriptorsArray_keepsStrings_dropsNonStrings() {
		TagSuggestion out = suggestWithModelJson("{\"descriptors\":[\"cotton\",42,\"slim\"]}");

		assertThat(out.descriptors()).containsExactly("cotton", "slim");
	}

	// --- Numeric clamp/validate (FR1.5, critical branch) ---

	@Test
	void formalityBelowRange_isLeftNull() {
		assertThat(suggestWithModelJson("{\"formality\":0}").formality()).isNull();
	}

	@Test
	void formalityAboveRange_isLeftNull() {
		assertThat(suggestWithModelJson("{\"formality\":9}").formality()).isNull();
	}

	@Test
	void warmthBelowRange_isLeftNull() {
		assertThat(suggestWithModelJson("{\"warmth\":0}").warmth()).isNull();
	}

	@Test
	void warmthAboveRange_isLeftNull() {
		assertThat(suggestWithModelJson("{\"warmth\":9}").warmth()).isNull();
	}

	@Test
	void nonNumericFormality_isLeftNull() {
		// A string where an integer is expected is not passed through and does not throw.
		assertThat(suggestWithModelJson("{\"formality\":\"high\"}").formality()).isNull();
	}

	// --- Fallback (FR1.6, critical branch) ---

	@Test
	void seamReturnsNull_returnsEmptySuggestion() {
		TagSuggestion out = suggestWithModelJson(null);

		assertThat(out).isEqualTo(TagSuggestion.empty());
	}

	@Test
	void seamReturnsBlank_returnsEmptySuggestion() {
		assertThat(suggestWithModelJson("   ")).isEqualTo(TagSuggestion.empty());
	}

	@Test
	void malformedJson_returnsEmptySuggestion_noThrow() {
		assertThat(suggestWithModelJson("{not valid json")).isEqualTo(TagSuggestion.empty());
	}

	@Test
	void apiError_seamThrows_returnsEmptySuggestion_noThrow() {
		when(imageProcessor.toResizedJpeg(PHOTO)).thenReturn(RESIZED);
		when(vision.extractTagsJson(RESIZED)).thenThrow(new RuntimeException("api error"));

		assertThat(service.suggest(PHOTO)).isEqualTo(TagSuggestion.empty());
	}

	@Test
	void timeout_seamThrows_returnsEmptySuggestion_noThrow() {
		when(imageProcessor.toResizedJpeg(PHOTO)).thenReturn(RESIZED);
		when(vision.extractTagsJson(RESIZED)).thenThrow(new IllegalStateException("timeout"));

		assertThat(service.suggest(PHOTO)).isEqualTo(TagSuggestion.empty());
	}

	// --- 400-vs-200 split (FR2.4): image guard failure must NOT be swallowed ---

	@Test
	void invalidImage_propagates_andSeamIsNeverCalled() {
		when(imageProcessor.toResizedJpeg(PHOTO))
			.thenThrow(new InvalidImageException("not an image"));

		assertThatThrownBy(() -> service.suggest(PHOTO))
			.isInstanceOf(InvalidImageException.class);
	}

	@Test
	void suggest_downsizesBeforeCallingTheModel() {
		// The bytes handed to the model are the resized JPEG, not the raw upload.
		suggestWithModelJson("{\"category\":\"top\"}");

		org.mockito.Mockito.verify(vision).extractTagsJson(RESIZED);
	}

	@Test
	void seamReturnsEmptyObject_returnsEmptySuggestion() {
		// Well-formed but empty JSON object — every field is absent, no field passes through.
		TagSuggestion out = suggestWithModelJson("{}");

		assertThat(out).isEqualTo(new TagSuggestion(null, null, null, null, null, null, null));
	}

	@Test
	void descriptors_ignoredList_doesNotAffectScalars() {
		TagSuggestion out = suggestWithModelJson(
			"{\"category\":\"jacket\",\"descriptors\":[]}");

		assertThat(out.category()).isEqualTo("Jacket");
		assertThat(out.descriptors()).isEqualTo(List.of());
	}
}
