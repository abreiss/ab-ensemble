package com.ensemble.security.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link CurrentUserIdArgumentResolver} so controllers can inject the
 * authenticated caller's {@code userId} via {@link CurrentUserId}.
 */
@Configuration
public class CurrentUserWebConfig implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserIdArgumentResolver());
	}
}
