package com.ensemble.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * Strict-TDD tests for the stylist guardrail — the grounded-reasoning heart of
 * the feature. The Claude client is a <strong>mocked</strong> {@link StylistModelClient}
 * seam and the wardrobe is a stubbed {@link WardrobeService}, so no key and no
 * network are involved. These cases drive 100% branch coverage of id-validation,
 * the one-retry rule, ungroundable handling, and the no-image-bytes guarantee.
 */
class StylistServiceTest {

	private final StylistModelClient model = mock(StylistModelClient.class);
	private final WardrobeService wardrobe = mock(WardrobeService.class);
	private final StylistService service = new StylistService(model, wardrobe);

	private static ItemResponse item(String id) {
		return new ItemResponse(id, "top", "navy", null, 3, "solid", 2, List.of("cotton"),
			"/api/items/" + id + "/photo", Instant.parse("2026-01-01T00:00:00Z"), null, 0);
	}

	private static String pick(String reason, String... ids) {
		String idList = ids.length == 0 ? "" : "\"" + String.join("\",\"", ids) + "\"";
		return "{\"itemIds\":[" + idList + "],\"reason\":\"" + reason + "\"}";
	}

	@Test
	void styleRequest_withValidOutput_returnsGroundedOutfit() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn(pick("navy layers", "a", "b"));

		Outfit outfit = service.style("streetwear today");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("navy layers");
		verify(model, times(1)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void styleRequest_withHallucinatedId_retriesOnceThenRendersValidSubset() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first try", "a", "ghost-id"))
			.thenReturn(pick("second try", "a", "b"));

		Outfit outfit = service.style("date night");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("second try");
		// Exactly one retry: the model is called twice, not more.
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void styleRequest_retryStillPartiallyInvalid_rendersOnlyValidSubset() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "ghost1"))
			.thenReturn(pick("second", "a", "ghost2"));

		Outfit outfit = service.style("casual");

		// Still-invalid id dropped; the valid subset is kept, reason from the final pick.
		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.reason()).isEqualTo("second");
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void styleRequest_allIdsInvalidAfterRetry_returnsError() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "x"))
			.thenReturn(pick("second", "y"));

		assertThatThrownBy(() -> service.style("formal"))
			.isInstanceOf(StylistUnavailableException.class);
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void styleRequest_withMalformedOutput_handledSafely() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn("{not json");

		// No invalid ids to feed back (empty pick) -> no retry -> ungroundable error, no crash.
		assertThatThrownBy(() -> service.style("anything"))
			.isInstanceOf(StylistUnavailableException.class);
		verify(model, times(1)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void styleRequest_sendsNoImageBytesToModel() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn(pick("ok", "a"));

		service.style("minimal");

		ArgumentCaptor<String> toolText = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model).proposeOutfit(toolText.capture(), convo.capture());
		// The wardrobe tool text carries ids + tags, but never the photo location/bytes.
		assertThat(toolText.getValue()).contains("a").contains("navy");
		assertThat(toolText.getValue()).doesNotContain("/api/items/a/photo").doesNotContain("photoUrl");
		// The conversation is plain text turns only.
		assertThat(convo.getValue()).allSatisfy(m -> assertThat(m.text()).isNotNull());
	}

	@Test
	void styleRequest_apiError_degradesGracefully() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList())).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> service.style("streetwear"))
			.isInstanceOf(StylistUnavailableException.class);
	}

	@Test
	void styleRequest_emptyWardrobe_returnsFriendlyResponse() {
		when(wardrobe.list()).thenReturn(List.of());

		Outfit outfit = service.style("streetwear today");

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isNotBlank();
		// The model is never invoked against an empty wardrobe.
		verifyNoInteractions(model);
	}

	@Test
	void styleRequest_retryFeedbackContainsInvalidIds() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "ghost-id"))
			.thenReturn(pick("second", "a"));

		service.style("something");

		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model, times(2)).proposeOutfit(anyString(), convo.capture());
		// The retry conversation feeds the specific invalid id back to the model.
		List<StylistMessage> retryTurns = convo.getAllValues().get(1);
		assertThat(retryTurns).anySatisfy(m -> assertThat(m.text()).contains("ghost-id"));
	}
}
