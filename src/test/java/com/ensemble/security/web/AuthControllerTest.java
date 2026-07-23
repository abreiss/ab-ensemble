package com.ensemble.security.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.SessionTokenService;
import com.ensemble.user.PasswordHasher;
import com.ensemble.user.User;
import com.ensemble.user.UserRepository;

/**
 * Slice test of the repurposed email/password login (issue #14). Verifies the success
 * contract, the non-enumerating generic {@code 401} for both unknown-email and wrong-password
 * (including that the timing-equalization bcrypt runs even when the email is absent), and the
 * sanitized {@code 400} on a malformed body. The {@code UserRepository}/{@code PasswordHasher}/
 * {@code SessionTokenService} collaborators are mocked so this proves our handling, not live
 * crypto or persistence.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserRepository users;

	@MockitoBean
	PasswordHasher passwordHasher;

	@MockitoBean
	SessionTokenService tokenService;

	private static User userWith(String userId, String email, String passwordHash) {
		User user = new User();
		user.setUserId(userId);
		user.setEmail(email);
		user.setPasswordHash(passwordHash);
		return user;
	}

	@Test
	void validLogin_returns200WithToken() throws Exception {
		when(users.findByEmail("demo@example.com"))
			.thenReturn(Optional.of(userWith("user-1", "demo@example.com", "stored-hash")));
		when(passwordHasher.matches("correcthorse", "stored-hash")).thenReturn(true);
		when(tokenService.issue("user-1")).thenReturn("token-xyz");

		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"demo@example.com\",\"password\":\"correcthorse\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("token-xyz"));
	}

	@Test
	void wrongPassword_returns401Generic() throws Exception {
		when(users.findByEmail("demo@example.com"))
			.thenReturn(Optional.of(userWith("user-1", "demo@example.com", "stored-hash")));
		when(passwordHasher.matches("wrong", "stored-hash")).thenReturn(false);

		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"demo@example.com\",\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"))
			.andExpect(jsonPath("$.message").value("invalid email or password"))
			.andExpect(jsonPath("$.token").doesNotExist());
		verify(tokenService, never()).issue(anyString());
	}

	@Test
	void unknownEmail_returns401Generic() throws Exception {
		when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ghost@example.com\",\"password\":\"whatever\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"))
			.andExpect(jsonPath("$.message").value("invalid email or password"))
			.andExpect(jsonPath("$.token").doesNotExist());
		// Non-enumeration: a bcrypt comparison still runs for an unknown email (dummy hash),
		// so response timing does not reveal whether the email is registered.
		verify(passwordHasher).matches(eq("whatever"), any());
		verify(tokenService, never()).issue(anyString());
	}

	@Test
	void malformedBody_returns400() throws Exception {
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
		verify(tokenService, never()).issue(anyString());
	}
}
