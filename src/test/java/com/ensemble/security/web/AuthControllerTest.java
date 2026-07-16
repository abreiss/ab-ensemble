package com.ensemble.security.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ensemble.security.SecurityProperties;
import com.ensemble.security.SessionTokenService;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SessionTokenService tokenService;

	@TestConfiguration
	static class RealSecurityPropertiesConfig {

		@Bean
		SecurityProperties securityProperties() {
			return new SecurityProperties("demo-pw", "demo-secret", null);
		}
	}

	@Test
	void correctPasscode_returns200WithToken() throws Exception {
		when(tokenService.issue()).thenReturn("token-abc");

		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"passcode\":\"demo-pw\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("token-abc"));
	}

	@Test
	void wrongPasscode_returns401NoToken() throws Exception {
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"passcode\":\"wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.token").doesNotExist());
		verifyNoInteractions(tokenService);
	}

	@Test
	void blankPasscode_returns401() throws Exception {
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"passcode\":\"\"}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(tokenService);
	}

	@Test
	void missingPasscodeField_returns401() throws Exception {
		mockMvc.perform(post("/api/auth")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}
}
