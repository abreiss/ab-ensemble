package com.ensemble.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.SessionTokenService;
import com.ensemble.security.SignupPasscodeVerifier;
import com.ensemble.user.DuplicateEmailException;
import com.ensemble.user.PasswordHasher;
import com.ensemble.user.User;
import com.ensemble.user.UserRepository;

/**
 * Slice test of the sign-up endpoint {@code POST /api/accounts} (issue #14). Proves the happy
 * path (201 + auto-login token), the duplicate-email conflict (409), the invite-only gate
 * (wrong signup passcode → 401 with <strong>no user created</strong>), and the three input
 * validation branches (short/blank password, invalid email, over-72-byte password → 400). The
 * {@code UserRepository}/{@code PasswordHasher}/{@code SignupPasscodeVerifier}/
 * {@code SessionTokenService} collaborators are mocked so this proves our handling, not live
 * crypto or persistence.
 */
@WebMvcTest(AccountController.class)
@Import(AccountControllerTest.FixedClockConfig.class)
class AccountControllerTest {

	/** A fixed instant so {@code createdAt} is deterministic, mirroring the injected Clock bean. */
	private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		Clock clock() {
			return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		}
	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserRepository users;

	@MockitoBean
	PasswordHasher passwordHasher;

	@MockitoBean
	SignupPasscodeVerifier signupPasscodeVerifier;

	@MockitoBean
	SessionTokenService tokenService;

	private static String body(String email, String password, String passcode) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"passcode\":\"" + passcode + "\"}";
	}

	@Test
	void validSignup_returns201WithToken() throws Exception {
		when(signupPasscodeVerifier.matches("good-code")).thenReturn(true);
		when(passwordHasher.hash("correcthorse")).thenReturn("bcrypt-hash");
		when(tokenService.issue(anyString())).thenReturn("token-abc");

		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("New@Example.com", "correcthorse", "good-code")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.token").value("token-abc"));

		// The persisted user carries a generated userId, the normalized email, and the *hashed*
		// password — never the raw one — and the auto-login token is minted for that same userId.
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).create(saved.capture());
		User created = saved.getValue();
		assertThat(created.getEmail()).isEqualTo("new@example.com");
		assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash");
		assertThat(created.getUserId()).isNotBlank();
		assertThat(created.getCreatedAt()).isEqualTo(FIXED_NOW);
		verify(tokenService).issue(created.getUserId());
	}

	@Test
	void duplicateEmail_returns409() throws Exception {
		when(signupPasscodeVerifier.matches("good-code")).thenReturn(true);
		when(passwordHasher.hash(anyString())).thenReturn("bcrypt-hash");
		doThrow(new DuplicateEmailException("email already registered"))
			.when(users).create(any(User.class));

		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("taken@example.com", "correcthorse", "good-code")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("conflict"))
			.andExpect(jsonPath("$.message").value("email already registered"))
			.andExpect(jsonPath("$.token").doesNotExist());
		verify(tokenService, never()).issue(anyString());
	}

	@Test
	void wrongSignupPasscode_returns401_noUserCreated() throws Exception {
		when(signupPasscodeVerifier.matches("wrong-code")).thenReturn(false);

		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("new@example.com", "correcthorse", "wrong-code")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.token").doesNotExist());

		// The invite gate rejected the request before any account was created.
		verify(users, never()).create(any(User.class));
		verify(tokenService, never()).issue(anyString());
	}

	@Test
	void blankOrShortPassword_returns400() throws Exception {
		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("new@example.com", "short", "good-code")))
			.andExpect(status().isBadRequest());

		// Validation fails on bind — before the passcode check or any user creation.
		verify(signupPasscodeVerifier, never()).matches(anyString());
		verify(users, never()).create(any(User.class));
	}

	@Test
	void invalidEmail_returns400() throws Exception {
		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("not-an-email", "correcthorse", "good-code")))
			.andExpect(status().isBadRequest());
		verify(users, never()).create(any(User.class));
	}

	@Test
	void passwordOver72Bytes_returns400() throws Exception {
		// 73 single-byte characters == 73 UTF-8 bytes, one over bcrypt's limit.
		String over72 = "a".repeat(73);

		mockMvc.perform(post("/api/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("new@example.com", over72, "good-code")))
			.andExpect(status().isBadRequest());
		verify(users, never()).create(any(User.class));
	}
}
