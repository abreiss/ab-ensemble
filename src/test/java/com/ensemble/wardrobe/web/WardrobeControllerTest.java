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

import com.ensemble.storage.InvalidImageException;
import com.ensemble.storage.PhotoNotFoundException;
import com.ensemble.wardrobe.ItemNotFoundException;
import com.ensemble.wardrobe.WardrobeService;
import com.ensemble.wardrobe.dto.ItemResponse;
import com.ensemble.wardrobe.dto.TagRequest;

@WebMvcTest(WardrobeController.class)
class WardrobeControllerTest {

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
		when(service.create(any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("primaryColor", "navy")
				.param("formality", "3")
				.param("warmth", "2"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/items/new-id"))
			.andExpect(jsonPath("$.itemId").value("new-id"))
			.andExpect(jsonPath("$.photoUrl").value("/api/items/new-id/photo"));
	}

	@Test
	void createItem_missingPhoto_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "2"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_invalidImage_returns400() throws Exception {
		when(service.create(any(), any())).thenThrow(new InvalidImageException("not an image"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "2"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_bindsMultipartTagFields_intoTagRequest() throws Exception {
		when(service.create(any(), any())).thenReturn(response("new-id"));

		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("primaryColor", "navy")
				.param("formality", "4")
				.param("warmth", "2"))
			.andExpect(status().isCreated());

		ArgumentCaptor<TagRequest> captor = ArgumentCaptor.forClass(TagRequest.class);
		verify(service).create(captor.capture(), any());
		TagRequest bound = captor.getValue();
		assertThat(bound.category()).isEqualTo("top");
		assertThat(bound.primaryColor()).isEqualTo("navy");
		assertThat(bound.formality()).isEqualTo(4);
		assertThat(bound.warmth()).isEqualTo(2);
	}

	@Test
	void createItem_formalityOutOfRange_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "9")
				.param("warmth", "2"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createItem_warmthOutOfRange_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items")
				.file(photoPart())
				.param("category", "top")
				.param("formality", "3")
				.param("warmth", "9"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listItems_returnsArray() throws Exception {
		when(service.list()).thenReturn(List.of(response("a"), response("b")));

		mockMvc.perform(get("/api/items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].itemId").value("a"));
	}

	@Test
	void getItem_returnsItem() throws Exception {
		when(service.get("a")).thenReturn(response("a"));

		mockMvc.perform(get("/api/items/a"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"))
			.andExpect(jsonPath("$.photoUrl").value("/api/items/a/photo"));
	}

	@Test
	void getItem_unknownId_returns404() throws Exception {
		when(service.get("nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(get("/api/items/nope"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void getPhoto_returnsJpegBytes() throws Exception {
		when(service.loadPhoto("a")).thenReturn(new byte[]{10, 20, 30});

		mockMvc.perform(get("/api/items/a/photo"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andExpect(content().bytes(new byte[]{10, 20, 30}));
	}

	@Test
	void getPhoto_unknownId_returns404() throws Exception {
		when(service.loadPhoto("nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(get("/api/items/nope/photo"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void getPhoto_whenPhotoFileMissing_returns404() throws Exception {
		// Record exists but its photo file is gone (inconsistent state) — degrade to a
		// clean 404 rather than an unhandled 500, and don't leak the internal key.
		when(service.loadPhoto("a")).thenThrow(new PhotoNotFoundException("a.jpg"));

		mockMvc.perform(get("/api/items/a/photo"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"))
			.andExpect(jsonPath("$.message").value("not found"));
	}

	@Test
	void updateTags_returnsUpdatedItem() throws Exception {
		when(service.updateTags(eq("a"), any())).thenReturn(response("a"));

		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":4,\"warmth\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"));
	}

	@Test
	void updateTags_formalityOutOfRange_returns400() throws Exception {
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":9,\"warmth\":2}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateTags_warmthOutOfRange_returns400() throws Exception {
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":3,\"warmth\":9}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void badRequest_bodyIsGeneric_noValidationInternalsLeak() throws Exception {
		// The error body must not echo verbose binding/validation internals.
		mockMvc.perform(put("/api/items/a/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":9,\"warmth\":2}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"))
			.andExpect(jsonPath("$.message").value("invalid request"));
	}

	@Test
	void updateTags_unknownId_returns404() throws Exception {
		when(service.updateTags(eq("nope"), any())).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(put("/api/items/nope/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"top\",\"formality\":3,\"warmth\":2}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void postWorn_valid_returns200WithUpdatedItem() throws Exception {
		ItemResponse worn = new ItemResponse("a", "top", "navy", null, 3, null, 2, List.of("cotton"),
			"/api/items/a/photo", Instant.parse("2026-07-13T00:00:00Z"),
			Instant.parse("2026-07-16T00:00:00Z"), 8);
		when(service.markWorn("a")).thenReturn(worn);

		mockMvc.perform(post("/api/items/a/worn"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemId").value("a"))
			.andExpect(jsonPath("$.wornCount").value(8))
			.andExpect(jsonPath("$.lastWorn").value("2026-07-16T00:00:00Z"));
	}

	@Test
	void postWorn_unknownId_returns404() throws Exception {
		when(service.markWorn("nope")).thenThrow(new ItemNotFoundException("nope"));

		mockMvc.perform(post("/api/items/nope/worn"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void deleteItem_returns204() throws Exception {
		doNothing().when(service).delete("a");

		mockMvc.perform(delete("/api/items/a"))
			.andExpect(status().isNoContent());
	}

	@Test
	void deleteItem_unknownId_returns404() throws Exception {
		doThrow(new ItemNotFoundException("nope")).when(service).delete("nope");

		mockMvc.perform(delete("/api/items/nope"))
			.andExpect(status().isNotFound());
	}
}
