package com.ensemble.wardrobe.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.web.SessionAuthFilter;
import com.ensemble.storage.InvalidImageException;
import com.ensemble.storage.PhotoNotFoundException;
import com.ensemble.wardrobe.ItemNotFoundException;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

@WebMvcTest(WardrobeController.class)
class WardrobeControllerTest {

	/**
	 * The authenticated caller. Set on each request via {@code .requestAttr(...)} — the same
	 * attribute {@link SessionAuthFilter} sets from a valid token — so {@code @CurrentUserId}
	 * resolves through {@code CurrentUserWebConfig} (auto-loaded by {@code @WebMvcTest}).
	 */
	private static final String USER = "userA";
	private static final String OTHER = "userB";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	WardrobeService service;

	private ItemResponse response(String id) {
		return new ItemResponse(id, "top", "navy", null, 3, null, 2, List.of("cotton"),
			"/api/items/" + id + "/photo", Instant.parse("2026-07-13T00:00:00Z"), null, 0);
	}

	private MockMultipartFile photoPart() {
		return new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});
	}

	@Test
	void createItem_multipart_returns201WithBodyAndLocation() throws Exception {
		when(service.create(any(), any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("primaryColor", "navy")
				.param("formality", "3")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/items/new-id"))
			.andExpect(jsonPath("$.itemId").value("new-id"))
			.andExpect(jsonPath("$.photoUrl").value("/api/items/new-id/photo"));
	}

	@Test
	void createItem_forwardsCallerUserId_toService() throws Exception {
		// Proves the create handler threads the authenticated caller into the service as the
		// owner of the new item (the write side of per-user scoping).
		when(service.create(any(), any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated());

		verify(service).create(eq(USER), any(), any());
	}

	@Test
	void createItem_missingPhoto_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_invalidImage_returns400() throws Exception {
		when(service.create(any(), any(), any())).thenThrow(new InvalidImageException("not an image"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_bindsMultipartTagFields_intoTagRequest() throws Exception {
		when(service.create(any(), any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("primaryColor", "navy")
				.param("formality", "4")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated());

		ArgumentCaptor<TagRequest> captor = ArgumentCaptor.forClass(TagRequest.class);
		verify(service).create(any(), captor.capture(), any());
		TagRequest bound = captor.getValue();
		assertThat(bound.category()).isEqualTo("top");
		assertThat(bound.primaryColor()).isEqualTo("navy");
		assertThat(bound.formality()).isEqualTo(4);
		assertThat(bound.warmth()).isEqualTo(2);
	}

	@Test
	void createItem_jewelryWithoutFormalityOrWarmth_returns201() throws Exception {
		// Jewelry has no formality/warmth — TagRequest must accept a null for each
		// (only category remains required), so the item still saves.
		when(service.create(any(), any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "Jewelry")
				.param("primaryColor", "gold")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isCreated());

		ArgumentCaptor<TagRequest> captor = ArgumentCaptor.forClass(TagRequest.class);
		verify(service).create(any(), captor.capture(), any());
		assertThat(captor.getValue().formality()).isNull();
		assertThat(captor.getValue().warmth()).isNull();
	}

	@Test
	void createItem_formalityOutOfRange_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "9")
				.param("warmth", "2")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_warmthOutOfRange_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "9")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listItems_returnsOnlyCallersItems() throws Exception {
		when(service.list(USER)).thenReturn(List.of(response("a"), response("b")));

		mockMvc.perform(get("/api/items")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].itemId").value("a"));
	}

	@Test
	void getItem_returnsItem() throws Exception {
		when(service.get(USER, "a")).thenReturn(response("a"));

		mockMvc.perform(get("/api/items/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"))
			.andExpect(jsonPath("$.photoUrl").value("/api/items/a/photo"));
	}

	@Test
	void getItem_unknownId_returns404() throws Exception {
		when(service.get(USER, "nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(get("/api/items/nope")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void getItem_otherUsersItem_returns404() throws Exception {
		// The service rejects a cross-user id exactly like a missing one; the controller must
		// forward the caller (userB) so the request 404s rather than returning userA's item.
		when(service.get(OTHER, "a")).thenThrow(new ItemNotFoundException("a"));

		mockMvc.perform(get("/api/items/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void getPhoto_returnsJpegBytes() throws Exception {
		when(service.loadPhoto(USER, "a")).thenReturn(new byte[]{10, 20, 30});

		mockMvc.perform(get("/api/items/a/photo")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(content().bytes(new byte[]{10, 20, 30}));
	}

	@Test
	void getPhoto_unknownId_returns404() throws Exception {
		when(service.loadPhoto(USER, "nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(get("/api/items/nope/photo")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void getPhoto_whenPhotoFileMissing_returns404() throws Exception {
		// Record exists but its photo file is gone (inconsistent state) — degrade to a
		// clean 404 rather than an unhandled 500, and don't leak the internal key.
		when(service.loadPhoto(USER, "a")).thenThrow(new PhotoNotFoundException("a.jpg"));

		mockMvc.perform(get("/api/items/a/photo")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"))
			.andExpect(jsonPath("$.message").value("not found"));
	}

	@Test
	void photo_otherUsersItem_returns404() throws Exception {
		// A cross-user photo request must 404 — never return the owner's image bytes. The
		// handler forwards the caller (userB) into loadPhoto → find(userId, itemId), so the
		// ownership choke point rejects it exactly like a missing id.
		when(service.loadPhoto(OTHER, "a")).thenThrow(new ItemNotFoundException("a"));

		mockMvc.perform(get("/api/items/a/photo")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void updateTags_returnsUpdatedItem() throws Exception {
		when(service.updateTags(eq(USER), eq("a"), any())).thenReturn(response("a"));

		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":4,\"warmth\":2}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"));
	}

	@Test
	void updateTags_formalityOutOfRange_returns400() throws Exception {
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":9,\"warmth\":2}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateTags_warmthOutOfRange_returns400() throws Exception {
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":3,\"warmth\":9}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest());
	}

	@Test
	void badRequest_bodyIsGeneric_noValidationInternalsLeak() throws Exception {
		// The error body must not echo verbose binding/validation internals.
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":9,\"warmth\":2}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"))
			.andExpect(jsonPath("$.message").value("invalid request"));
	}

	@Test
	void updateTags_unknownId_returns404() throws Exception {
		when(service.updateTags(eq(USER), eq("nope"), any())).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(put("/api/items/nope/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":3,\"warmth\":2}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateTags_otherUsersItem_returns404() throws Exception {
		when(service.updateTags(eq(OTHER), eq("a"), any())).thenThrow(new ItemNotFoundException("a"));

		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":3,\"warmth\":2}")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void postWorn_valid_returns200WithUpdatedItem() throws Exception {
		ItemResponse worn = new ItemResponse("a", "top", "navy", null, 3, null, 2, List.of("cotton"),
			"/api/items/a/photo", Instant.parse("2026-07-13T00:00:00Z"),
			Instant.parse("2026-07-16T00:00:00Z"), 8);
		when(service.markWorn(USER, "a")).thenReturn(worn);

		mockMvc.perform(post("/api/items/a/worn")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"))
			.andExpect(jsonPath("$.wornCount").value(8))
			.andExpect(jsonPath("$.lastWorn").value("2026-07-16T00:00:00Z"));
	}

	@Test
	void postWorn_unknownId_returns404() throws Exception {
		when(service.markWorn(USER, "nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(post("/api/items/nope/worn")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void postWorn_otherUsersItem_returns404() throws Exception {
		when(service.markWorn(OTHER, "a")).thenThrow(new ItemNotFoundException("a"));

		mockMvc.perform(post("/api/items/a/worn")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void deleteItem_returns204() throws Exception {
		doNothing().when(service).delete(USER, "a");

		mockMvc.perform(delete("/api/items/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNoContent());
	}

	@Test
	void deleteItem_unknownId_returns404() throws Exception {
		doThrow(new ItemNotFoundException("nope")).when(service).delete(USER, "nope");

		mockMvc.perform(delete("/api/items/nope")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, USER))
			.andExpect(status().isNotFound());
	}

	@Test
	void deleteItem_otherUsersItem_returns404() throws Exception {
		doThrow(new ItemNotFoundException("a")).when(service).delete(OTHER, "a");

		mockMvc.perform(delete("/api/items/a")
				.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, OTHER))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}
}
