package com.ensemble.stylist.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.stylist.Outfit;
import com.ensemble.stylist.StylistMessage;
import com.ensemble.stylist.StylistService;
import com.ensemble.stylist.StylistUnavailableException;
import com.ensemble.usage.CallCapService;
import com.ensemble.usage.DailyCapExceededException;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;

/**
 * MockMvc contract + error-path tests for {@code POST /api/style}. The
 * {@link StylistService} is mocked, so no key or network is involved — these
 * assert the request/response DTO shape and that upstream/ungroundable failures
 * map through {@code ApiExceptionHandler} to a graceful error.
 */
@WebMvcTest(StyleController.class)
class StyleControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	StylistService service;

	@MockitoBean
	CallCapService callCapService;

	@MockitoBean
	WardrobeService wardrobe;

	private static ItemResponse item(String id, String category, String color, int formality, int warmth) {
		return new ItemResponse(id, category, color, null, formality, "solid", warmth, List.of("linen"),
			"/api/items/" + id + "/photo", Instant.parse("2026-01-01T00:00:00Z"), null, 0);
	}

	@Test
	void postStyle_enrichesItemsWithRationaleAndStoredTags() throws Exception {
		when(service.style(anyString(), anyList())).thenReturn(new Outfit(
			List.of("a", "b"), "brunch-ready",
			Map.of("a", "breathes on a warm morning", "b", "earthy tone lifts the look")));
		when(wardrobe.list()).thenReturn(List.of(
			item("a", "shirt", "white", 3, 2), item("b", "chinos", "olive", 3, 2)));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"brunch\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].itemId").value("a"))
			.andExpect(jsonPath("$.items[0].rationale").value("breathes on a warm morning"))
			.andExpect(jsonPath("$.items[0].category").value("shirt"))
			.andExpect(jsonPath("$.items[0].primaryColor").value("white"))
			.andExpect(jsonPath("$.items[0].formality").value(3))
			.andExpect(jsonPath("$.items[0].warmth").value(2))
			.andExpect(jsonPath("$.items[0].descriptors[0]").value("linen"))
			.andExpect(jsonPath("$.items[1].rationale").value("earthy tone lifts the look"))
			.andExpect(jsonPath("$.items[1].category").value("chinos"));
	}

	@Test
	void postStyle_valid_returns200WithOutfit() throws Exception {
		when(service.style(anyString(), anyList()))
			.thenReturn(new Outfit(List.of("a", "b"), "navy layers read intentional"));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"streetwear today\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemIds.length()").value(2))
			.andExpect(jsonPath("$.itemIds[0]").value("a"))
			.andExpect(jsonPath("$.reason").value("navy layers read intentional"))
			.andExpect(jsonPath("$.items[0].itemId").value("a"))
			.andExpect(jsonPath("$.items[0].photoUrl").value("/api/items/a/photo"))
			.andExpect(jsonPath("$.items[1].photoUrl").value("/api/items/b/photo"));
	}

	@Test
	void postStyle_emptyWardrobe_returnsFriendlyResponse() throws Exception {
		when(service.style(anyString(), anyList()))
			.thenReturn(new Outfit(List.of(), "Your wardrobe is empty — add a few pieces first."));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"streetwear today\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemIds.length()").value(0))
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.reason").value(Matchers.not(Matchers.blankOrNullString())));
	}

	@Test
	void postStyle_upstreamFailure_returnsGracefulError() throws Exception {
		when(service.style(anyString(), anyList()))
			.thenThrow(new StylistUnavailableException("The stylist is unavailable right now."));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"streetwear today\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error").value("stylist_unavailable"));
	}

	@Test
	void postStyle_overDailyCap_returns429() throws Exception {
		doThrow(new DailyCapExceededException("daily call limit reached, try again tomorrow"))
			.when(callCapService).reserve();

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"streetwear today\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error").value("daily_cap_exceeded"));

		verifyNoInteractions(service);
	}

	@Test
	void postStyle_withHistory_returns200WithOutfit() throws Exception {
		when(service.style(anyString(), anyList()))
			.thenReturn(new Outfit(List.of("b"), "a fresh, bolder look"));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"too plain\",\"history\":["
					+ "{\"role\":\"user\",\"text\":\"streetwear today\"},"
					+ "{\"role\":\"assistant\",\"text\":\"chose a and b\"}]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemIds.length()").value(1))
			.andExpect(jsonPath("$.itemIds[0]").value("b"))
			.andExpect(jsonPath("$.reason").value("a fresh, bolder look"))
			.andExpect(jsonPath("$.items[0].photoUrl").value("/api/items/b/photo"));

		// The history array is mapped to ordered, typed conversation turns.
		ArgumentCaptor<List<StylistMessage>> convo = ArgumentCaptor.captor();
		verify(service).style(eq("too plain"), convo.capture());
		assertThat(convo.getValue()).extracting(StylistMessage::text)
			.containsExactly("streetwear today", "chose a and b");
		assertThat(convo.getValue()).extracting(StylistMessage::role)
			.containsExactly(StylistMessage.Role.USER, StylistMessage.Role.ASSISTANT);
	}

	@Test
	void postStyle_withHistory_upstreamFailure_returnsGracefulError() throws Exception {
		when(service.style(anyString(), anyList()))
			.thenThrow(new StylistUnavailableException("The stylist is unavailable right now."));

		mockMvc.perform(post("/api/style")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\":\"too plain\",\"history\":["
					+ "{\"role\":\"user\",\"text\":\"streetwear today\"}]}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error").value("stylist_unavailable"));
	}
}
