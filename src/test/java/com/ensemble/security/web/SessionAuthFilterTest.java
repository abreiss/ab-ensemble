package com.ensemble.security.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.SessionTokenService;
import com.ensemble.wardrobe.WardrobeService;

/**
 * Full-context test (real {@code SecurityConfig} filter registration) so the actual
 * gate scope and header/query token acceptance are exercised end-to-end, not just the
 * filter's unit logic. {@link WardrobeService} is mocked so no live DynamoDB is needed —
 * this stays a fast, isolated test per docs/TESTING.md.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SessionAuthFilterTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SessionTokenService tokenService;

	@MockitoBean
	WardrobeService wardrobeService;

	@Test
	void protectedApi_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/items"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedApi_withValidToken_passesThrough() throws Exception {
		when(wardrobeService.list()).thenReturn(List.of());

		mockMvc.perform(get("/api/items").header("X-Ensemble-Session", tokenService.issue()))
			.andExpect(status().isOk());
	}

	@Test
	void protectedApi_withValidQueryToken_passesThrough() throws Exception {
		when(wardrobeService.list()).thenReturn(List.of());

		mockMvc.perform(get("/api/items").param("token", tokenService.issue()))
			.andExpect(status().isOk());
	}

	@Test
	void protectedApi_withInvalidToken_returns401() throws Exception {
		mockMvc.perform(get("/api/items").header("X-Ensemble-Session", "garbage"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void health_isOpenWithoutToken() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk());
	}

	@Test
	void auth_isOpenWithoutToken() throws Exception {
		// Reaches AuthController (which itself rejects the wrong passcode with a 401)
		// rather than being short-circuited by the gate filter — proven by using the
		// real test passcode and expecting success, not just "some 401".
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"passcode\":\"test-passcode\"}"))
			.andExpect(status().isOk());
	}
}
