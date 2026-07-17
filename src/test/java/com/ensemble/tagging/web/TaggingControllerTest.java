package com.ensemble.tagging.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.ensemble.storage.InvalidImageException;
import com.ensemble.tagging.TaggingService;
import com.ensemble.tagging.dto.TagSuggestion;
import com.ensemble.usage.CallCapService;
import com.ensemble.usage.DailyCapExceededException;

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

	@MockitoBean
	CallCapService callCapService;

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

		// A request rejected before reaching the controller body must not consume
		// the shared daily-cap counter (the counter only reflects accepted requests).
		verifyNoInteractions(callCapService);
	}

	@Test
	void tag_nonImage_serviceThrowsInvalidImage_returns400_sanitizedBody() throws Exception {
		when(service.suggest(any())).thenThrow(new InvalidImageException("not an image"));

		mockMvc.perform(multipart("/api/items/tag").file(photoPart()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("bad_request"))
			.andExpect(jsonPath("$.message").value("invalid request"));
	}

	@Test
	void tag_photoReadFails_throwsInvalidImage_andServiceNeverCalled() throws Exception {
		// A truncated/aborted upload makes photo.getBytes() throw IOException. The controller
		// must translate that into a bad-request InvalidImageException (→ sanitized 400 via the
		// shared advice), not let a raw IOException escape into a 500.
		MultipartFile photo = mock(MultipartFile.class);
		when(photo.getBytes()).thenThrow(new IOException("truncated upload"));

		assertThatThrownBy(() -> new TaggingController(service, mock(CallCapService.class)).tag(photo))
			.isInstanceOf(InvalidImageException.class);
		verifyNoInteractions(service);
	}

	@Test
	void tag_overDailyCap_returns429() throws Exception {
		doThrow(new DailyCapExceededException("daily call limit reached, try again tomorrow"))
			.when(callCapService).reserve();

		mockMvc.perform(multipart("/api/items/tag").file(photoPart()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error").value("daily_cap_exceeded"));

		verifyNoInteractions(service);
	}
}
