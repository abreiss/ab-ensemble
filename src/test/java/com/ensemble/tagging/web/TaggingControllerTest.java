package com.ensemble.tagging.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.storage.InvalidImageException;
import com.ensemble.tagging.TaggingService;
import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Web-layer contract for {@code POST /api/items/tag} with a <strong>mocked</strong>
 * {@code TaggingService}: a good suggestion returns {@code 200} JSON, a degraded/empty
 * suggestion still returns {@code 200} (never {@code 500}), and a missing/invalid photo
 * returns a sanitized {@code 400} via the shared {@code ApiExceptionHandler}.
 */
@WebMvcTest(TaggingController.class)
class TaggingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	TaggingService service;

	private MockMultipartFile photoPart() {
		return new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});
	}

	@Test
	void tag_goodSuggestion_returns200Json() throws Exception {
		when(service.suggest(any())).thenReturn(
			new TagSuggestion("top", "navy", "white", 3, "striped", 2, List.of("cotton", "slim")));

		mockMvc.perform(multipart("/api/items/tag").file(photoPart()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.category").value("top"))
			.andExpect(jsonPath("$.primaryColor").value("navy"))
			.andExpect(jsonPath("$.formality").value(3))
			.andExpect(jsonPath("$.warmth").value(2))
			.andExpect(jsonPath("$.descriptors[0]").value("cotton"));
	}

	@Test
	void tag_degradedEmptySuggestion_stillReturns200() throws Exception {
		// A vision failure/timeout/malformed body degrades to an empty suggestion — never 500.
		when(service.suggest(any())).thenReturn(TagSuggestion.empty());

		mockMvc.perform(multipart("/api/items/tag").file(photoPart()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.category").value(nullValue()))
			.andExpect(jsonPath("$.formality").value(nullValue()))
			.andExpect(jsonPath("$.descriptors").value(nullValue()));
	}

	@Test
	void tag_missingPhotoPart_returns400() throws Exception {
		mockMvc.perform(multipart("/api/items/tag"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void tag_nonImage_serviceThrowsInvalidImage_returns400_sanitizedBody() throws Exception {
		when(service.suggest(any())).thenThrow(new InvalidImageException("not an image"));

		mockMvc.perform(multipart("/api/items/tag").file(photoPart()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"))
			.andExpect(jsonPath("$.message").value("invalid request"));
	}
}
