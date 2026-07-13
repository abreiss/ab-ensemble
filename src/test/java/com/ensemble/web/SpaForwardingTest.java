package com.ensemble.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ensemble.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(SpaForwardingConfig.class)
class SpaForwardingTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void root_servesSpaIndex() throws Exception {
		// The root is served by Spring Boot's welcome-page mapping, which forwards
		// to the SPA entry; the forward is resolved to index.html content at runtime
		// (proven via curl against the running jar in Task 3.4).
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));
	}

	@Test
	void clientRoute_fallsBackToSpaIndex() throws Exception {
		mockMvc.perform(get("/some/client/route"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("SPA_INDEX_FIXTURE")));
	}

	@Test
	void apiRoute_isNotShadowedBySpaFallback() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"ok\"}"));
	}

	@Test
	void unknownApiPath_returns404_notSpaShell() throws Exception {
		// The SPA fallback must not swallow unmapped API routes: an unknown
		// /api/** path returns 404 rather than 200 with the SPA shell.
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isNotFound());
	}

	@Test
	void staticAsset_isServedDirectly_notSpaIndex() throws Exception {
		mockMvc.perform(get("/assets/probe.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("SPA_ASSET_FIXTURE")));
	}
}
