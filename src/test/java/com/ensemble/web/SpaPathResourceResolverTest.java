package com.ensemble.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for the SPA resource resolver, exercising its branches directly
 * (without the Spring MVC welcome-page mapping in front of it). The location is
 * the test-classpath {@code static/} folder, which holds an index.html fixture
 * and an {@code assets/probe.js} asset fixture.
 */
class SpaPathResourceResolverTest {

	private final SpaForwardingConfig.SpaPathResourceResolver resolver =
			new SpaForwardingConfig.SpaPathResourceResolver();
	private final Resource location = new ClassPathResource("static/");

	@Test
	void emptyPath_resolvesToSpaShell() throws IOException {
		Resource resolved = resolver.getResource("", location);

		assertThat(resolved).isNotNull();
		assertThat(resolved.getFilename()).isEqualTo("index.html");
	}

	@Test
	void unknownClientRoute_fallsBackToSpaShell() throws IOException {
		Resource resolved = resolver.getResource("some/client/route", location);

		assertThat(resolved).isNotNull();
		assertThat(resolved.getFilename()).isEqualTo("index.html");
	}

	@Test
	void apiPath_returnsNull_soItIsNeverRewrittenToTheSpaShell() throws IOException {
		Resource resolved = resolver.getResource("api/does-not-exist", location);

		assertThat(resolved).isNull();
	}

	@Test
	void existingAsset_isServedDirectly_notTheSpaShell() throws IOException {
		Resource resolved = resolver.getResource("assets/probe.js", location);

		assertThat(resolved).isNotNull();
		assertThat(resolved.getFilename()).isEqualTo("probe.js");
	}
}
