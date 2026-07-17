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

	/** A `pieces`-shaped pick: alternating id then rationale, e.g. pieces("r","a","ra","b","rb"). */
	private static String pieces(String reason, String... idThenRationale) {
		StringBuilder sb = new StringBuilder("{\"reason\":\"").append(reason).append("\",\"pieces\":[");
		for (int i = 0; i + 1 < idThenRationale.length; i += 2) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"itemId\":\"").append(idThenRationale[i])
				.append("\",\"rationale\":\"").append(idThenRationale[i + 1]).append("\"}");
		}
		return sb.append("]}").toString();
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
	void styleRequest_withPieces_carriesRationaleForGroundedIdsOnly() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		// The model returns per-item rationale for a, b, and a hallucinated ghost id.
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pieces("brunch", "a", "breathes well", "b", "earthy tone", "ghost", "not owned"));

		Outfit outfit = service.style("brunch");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.rationaleFor("a")).isEqualTo("breathes well");
		assertThat(outfit.rationaleFor("b")).isEqualTo("earthy tone");
		// The hallucinated id is dropped along with its rationale — only grounded ids survive.
		assertThat(outfit.rationaleById()).containsOnlyKeys("a", "b");
	}

	@Test
	void styleRequest_withPieces_hallucinatedId_retriesOnceThenCarriesRationale() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pieces("first", "a", "ok", "ghost", "bad"))
			.thenReturn(pieces("second", "a", "clean base", "b", "adds contrast"));

		Outfit outfit = service.style("date night");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("second");
		assertThat(outfit.rationaleFor("b")).isEqualTo("adds contrast");
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
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

	@Test
	void styleRequest_firstPickRetry_injectsNoAssistantTurn() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "ghost-id"))
			.thenReturn(pick("second", "a"));

		// A first-turn request (no history) that hallucinates an id and retries.
		service.style("something");

		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model, times(2)).proposeOutfit(anyString(), convo.capture());
		List<StylistMessage> retryTurns = convo.getAllValues().get(1);
		// The grounding retry must NOT introduce an assistant turn: the model client
		// reads "a prior assistant turn" as a pushback re-pick and would then inject the
		// "produce a DIFFERENT outfit" nudge — wrong for a first-pick correction.
		assertThat(retryTurns).noneMatch(m -> m.role() == StylistMessage.Role.ASSISTANT);
		// The specific invalid id is still fed back so grounding is corrected.
		assertThat(retryTurns).anySatisfy(m -> assertThat(m.text()).contains("ghost-id"));
	}

	// --- Stateless multi-turn re-pick (Unit 2): style(vibe, history) overload ---

	@Test
	void style_withHistory_forwardsPriorTurnsToModel() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn(pick("ok", "a"));

		service.style("too plain",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a,b")));

		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model).proposeOutfit(anyString(), convo.capture());
		// Prior turns are replayed in order, then the current vibe is appended last.
		assertThat(convo.getValue()).extracting(StylistMessage::text)
			.containsExactly("streetwear", "chose a,b", "too plain");
		assertThat(convo.getValue()).extracting(StylistMessage::role)
			.containsExactly(StylistMessage.Role.USER, StylistMessage.Role.ASSISTANT, StylistMessage.Role.USER);
	}

	@Test
	void style_emptyHistory_matchesSingleTurn() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn(pick("ok", "a", "b"));

		Outfit single = service.style("minimal");
		Outfit empty = service.style("minimal", List.of());

		assertThat(single.itemIds()).isEqualTo(empty.itemIds());
		assertThat(single.reason()).isEqualTo(empty.reason());
		// Both entry points seed the identical single-turn conversation.
		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model, times(2)).proposeOutfit(anyString(), convo.capture());
		assertThat(convo.getAllValues().get(0)).extracting(StylistMessage::text).containsExactly("minimal");
		assertThat(convo.getAllValues().get(1)).extracting(StylistMessage::text).containsExactly("minimal");
	}

	@Test
	void style_repick_staysGroundedWithHallucinatedIdRetriedOnce() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "a", "ghost-id"))
			.thenReturn(pick("second", "a", "b"));

		Outfit outfit = service.style("too plain",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a,b")));

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("second");
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void style_repick_rendersValidSubsetAfterRetry() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("first", "ghost1"))
			.thenReturn(pick("second", "a", "ghost2"));

		Outfit outfit = service.style("casual",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a")));

		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.reason()).isEqualTo("second");
		verify(model, times(2)).proposeOutfit(anyString(), anyList());
	}

	@Test
	void style_repick_sendsNoImageBytes() {
		when(wardrobe.list()).thenReturn(List.of(item("a")));
		when(model.proposeOutfit(anyString(), anyList())).thenReturn(pick("ok", "a"));

		service.style("too plain",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a")));

		ArgumentCaptor<String> toolText = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model).proposeOutfit(toolText.capture(), convo.capture());
		assertThat(toolText.getValue()).doesNotContain("/api/items/a/photo").doesNotContain("photoUrl");
		// Every forwarded turn — history and current — is plain text, never a photo path.
		assertThat(convo.getValue()).allSatisfy(m -> assertThat(m.text()).isNotNull());
		assertThat(convo.getValue()).noneSatisfy(m -> assertThat(m.text()).contains("/api/items"));
	}

	@Test
	void style_repick_emptyWardrobe_returnsFriendly() {
		when(wardrobe.list()).thenReturn(List.of());

		Outfit outfit = service.style("too plain",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a")));

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isNotBlank();
		// An empty wardrobe short-circuits even when re-pick history is present.
		verifyNoInteractions(model);
	}

	@Test
	void style_repeatedPushback_eachPickGrounded() {
		when(wardrobe.list()).thenReturn(List.of(item("a"), item("b")));
		when(model.proposeOutfit(anyString(), anyList()))
			.thenReturn(pick("round one", "a"))
			.thenReturn(pick("round two", "b"));

		// First pushback round.
		Outfit firstRepick = service.style("bolder",
			List.of(StylistMessage.user("streetwear"), StylistMessage.assistant("chose a")));
		assertThat(firstRepick.itemIds()).containsExactly("a");

		// Second pushback round carries the whole accumulated thread.
		List<StylistMessage> fullThread = List.of(
			StylistMessage.user("streetwear"),
			StylistMessage.assistant("chose a"),
			StylistMessage.user("bolder"),
			StylistMessage.assistant("chose a again"));
		Outfit secondRepick = service.style("even bolder", fullThread);
		assertThat(secondRepick.itemIds()).containsExactly("b");

		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(model, times(2)).proposeOutfit(anyString(), convo.capture());
		// The later pick forwards the full accumulated thread plus the newest vibe.
		assertThat(convo.getAllValues().get(1)).extracting(StylistMessage::text)
			.containsExactly("streetwear", "chose a", "bolder", "chose a again", "even bolder");
	}
}
