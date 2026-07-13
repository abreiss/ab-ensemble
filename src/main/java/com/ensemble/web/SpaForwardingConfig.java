package com.ensemble.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built single-page app from the classpath and falls back to
 * {@code index.html} for client-side routes, so deep links and browser
 * refreshes resolve to the SPA instead of 404-ing.
 *
 * <p>The fallback must never shadow the JSON API: requests under {@code /api/**}
 * are handled by their controllers (which take precedence over this resource
 * handler), and as a defensive guard the resolver refuses to serve the SPA
 * shell for {@code api/} paths.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

	private static final String STATIC_LOCATION = "classpath:/static/";
	private static final Resource INDEX = new ClassPathResource("static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations(STATIC_LOCATION)
				.resourceChain(true)
				.addResolver(new SpaPathResourceResolver());
	}

	/**
	 * Returns the real asset when it exists, otherwise the SPA shell — except for
	 * {@code api/} paths, which are never rewritten to {@code index.html}.
	 */
	static final class SpaPathResourceResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			if (resourcePath.isEmpty()) {
				return spaShell();
			}
			Resource requested = location.createRelative(resourcePath);
			if (requested.exists() && requested.isReadable()) {
				return requested;
			}
			if (resourcePath.startsWith("api/")) {
				return null;
			}
			return spaShell();
		}

		private Resource spaShell() {
			return INDEX.exists() ? INDEX : null;
		}
	}
}
