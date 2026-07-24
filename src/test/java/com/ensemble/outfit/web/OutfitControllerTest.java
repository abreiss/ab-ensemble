package com.ensemble.outfit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.outfit.InvalidOutfitException;
import com.ensemble.outfit.OutfitNotFoundException;
import com.ensemble.outfit.OutfitService;
import com.ensemble.outfit.dto.OutfitResponse;
import com.ensemble.outfit.dto.SaveOutfitRequest;
import com.ensemble.security.web.SessionAuthFilter;

@WebMvcTest(OutfitController.class)
class OutfitControllerTest {

	/** The authenticated caller, set via the request attribute the session filter would set. */
	private static final String USER = "userA";
	private static final String OTHER = "userB";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OutfitService service;

	private OutfitResponse response(String id) {
		return new OutfitResponse(id, List.of("item-a", "item-b"), "manual", null,
			Instant.parse("2026-07-13T00:00:00Z"));
	}

	@Test
	void saveOutfit_valid_returns201WithBodyAndLocation() throws Exception {
		when(service.create(any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"item-a\",\"item-b\"],\"source\":\"manual\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/outfits/new-id"))
			.andExpect(jsonPath("$.outfitId").value("new-id"))
			.andExpect(jsonPath("$.itemIds.length()").value(2))
			.andExpect(jsonPath("$.source").value("manual"));
	}

	@Test
	void saveOutfit_forwardsCallerUserId_toService() throws Exception {
		// Proves the save handler threads the authenticated caller into the service, so the
		// outfit is grounded against and stamped with that user (the write side of scoping).
		when(service.create(any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"item-a\"],\"source\":\"manual\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated());

		verify(service).create(eq(USER), any());
	}

	@Test
	void saveOutfit_bindsJsonBody_intoSaveOutfitRequest() throws Exception {
		when(service.create(any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"item-a\"],\"source\":\"ai\",\"reason\":\"anchor piece\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated());

		ArgumentCaptor<SaveOutfitRequest> captor = ArgumentCaptor.forClass(SaveOutfitRequest.class);
		verify(service).create(any(), captor.capture());
		SaveOutfitRequest bound = captor.getValue();
		assertThat(bound.itemIds()).containsExactly("item-a");
		assertThat(bound.source()).isEqualTo("ai");
		assertThat(bound.reason()).isEqualTo("anchor piece");
	}

	@Test
	void saveOutfit_serviceRejectsUnknownId_returns400BadRequest() throws Exception {
		when(service.create(any(), any())).thenThrow(new InvalidOutfitException("unknown item id: ghost"));

		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"ghost\"],\"source\":\"manual\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"));
	}

	@Test
	void saveOutfit_emptyItemIds_returns400FromBeanValidation() throws Exception {
		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[],\"source\":\"manual\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"));
	}

	@Test
	void saveOutfit_badSource_returns400FromBeanValidation() throws Exception {
		mockMvc.perform(post("/api/outfits")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[\"item-a\"],\"source\":\"robot\"}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listOutfits_returnsOnlyCallersOutfits() throws Exception {
		when(service.list(USER)).thenReturn(List.of(response("a"), response("b")));

		mockMvc.perform(get("/api/outfits")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].outfitId").value("a"));
	}

	@Test
	void deleteOutfit_returns204() throws Exception {
		doNothing().when(service).delete(USER, "a");

		mockMvc.perform(delete("/api/outfits/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNoContent());
	}

	@Test
	void deleteOutfit_unknownId_returns404NotFound() throws Exception {
		doThrow(new OutfitNotFoundException("nope")).when(service).delete(USER, "nope");

		mockMvc.perform(delete("/api/outfits/nope")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void deleteOutfit_otherUsersOutfit_returns404() throws Exception {
		// The service rejects a cross-user outfit id exactly like a missing one; the controller
		// must forward the caller (userB) so the delete 404s rather than removing userA's outfit.
		doThrow(new OutfitNotFoundException("a")).when(service).delete(OTHER, "a");

		mockMvc.perform(delete("/api/outfits/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}
}
