package com.ensemble.stylist.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.stylist.Outfit;
import com.ensemble.stylist.StylistService;
import com.ensemble.stylist.StylistUnavailableException;
import com.ensemble.usage.CallCapService;
import com.ensemble.usage.DailyCapExceededException;

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

	@Test
	void postStyle_valid_returns200WithOutfit() throws Exception {
		when(service.style(anyString())).thenReturn(new Outfit(List.of("a", "b"), "navy layers read intentional"));

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
		when(service.style(anyString()))
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
		when(service.style(anyString()))
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
}
