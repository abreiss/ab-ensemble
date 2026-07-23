package com.ensemble.security.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.SessionTokenService;
import com.ensemble.user.User;
import com.ensemble.user.UserRepository;
import com.ensemble.wardrobe.WardrobeService;

/**
 * Full-context test (real {@code SecurityConfig} filter registration + the
 * {@code CurrentUserId} argument resolver) so the actual gate scope, header/query token
 * acceptance, and end-to-end principal resolution are exercised, not just the filter's unit
 * logic. {@link WardrobeService} and {@link UserRepository} are mocked so no live DynamoDB is
 * needed — this stays a fast, isolated test per docs/TESTING.md.
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

	@MockitoBean
	UserRepository userRepository;

	private static User userWith(String userId, String email) {
		User user = new User();
		user.setUserId(userId);
		user.setEmail(email);
		return user;
	}

	@Test
	void protectedApi_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/items"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void validToken_resolvesUserId_andPassesThrough() throws Exception {
		when(wardrobeService.list()).thenReturn(List.of());

		mockMvc.perform(get("/api/items").header("X-Ensemble-Session", tokenService.issue("user-1")))
			.andExpect(status().isOk());
	}

	@Test
	void protectedApi_withValidQueryToken_passesThrough() throws Exception {
		when(wardrobeService.list()).thenReturn(List.of());

		mockMvc.perform(get("/api/items").param("token", tokenService.issue("user-1")))
			.andExpect(status().isOk());
	}

	@Test
	void protectedApi_withInvalidToken_returns401() throws Exception {
		mockMvc.perform(get("/api/items").header("X-Ensemble-Session", "garbage"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authAndHealth_areOpen() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk());

		// POST /api/auth is open: it reaches AuthController (which returns the *login* 401
		// with the "invalid email or password" body) rather than being short-circuited by
		// the gate filter (whose 401 body is "authentication required").
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\",\"password\":\"whatever\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("invalid email or password"));
	}

	@Test
	void me_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void me_withValidToken_returns200WithUserIdAndEmail() throws Exception {
		when(userRepository.findByUserId("user-42"))
			.thenReturn(Optional.of(userWith("user-42", "demo@example.com")));

		mockMvc.perform(get("/api/me").header("X-Ensemble-Session", tokenService.issue("user-42")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value("user-42"))
			.andExpect(jsonPath("$.email").value("demo@example.com"));
	}

	@Test
	void me_withValidTokenButUnknownUser_returns401() throws Exception {
		when(userRepository.findByUserId("ghost")).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/me").header("X-Ensemble-Session", tokenService.issue("ghost")))
			.andExpect(status().isUnauthorized());
	}
}
