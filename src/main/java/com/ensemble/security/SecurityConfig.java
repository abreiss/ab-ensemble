package com.ensemble.security;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.ensemble.security.web.SessionAuthFilter;

import jakarta.annotation.PostConstruct;

/**
 * Wires the passcode gate: the {@link SecurityProperties} config record, the pure
 * {@link SessionTokenService} bean, and the {@link SessionAuthFilter} gate.
 *
 * <p>The filter is registered via {@link FilterRegistrationBean} — deliberately
 * <strong>not</strong> exposed as a {@code @Component}/raw {@code Filter} bean — for two
 * reasons: (1) Spring Boot auto-registers any raw {@code Filter} bean against every URL
 * (`/*`) unless it is wrapped in a registration bean with explicit {@code urlPatterns},
 * and (2) a {@code FilterRegistrationBean} is not a controller/{@code @ControllerAdvice}/
 * converter/etc., so it falls outside what {@code @WebMvcTest}'s narrow component scan
 * includes — the existing specs 01-06 {@code @WebMvcTest} slices stay token-free. Full
 * {@code @SpringBootTest} contexts (see {@code SessionAuthFilterTest}) load the whole
 * application and do apply it, scoped to {@code /api/*} and ordered first so no
 * downstream check (e.g. the daily cap) runs before authentication.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private final SecurityProperties properties;

	public SecurityConfig(SecurityProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void logGateState() {
		if (!properties.passcodeConfigured()) {
			log.warn("ensemble.security.passcode is blank — the signup gate is closed "
				+ "(every POST /api/accounts attempt will be rejected) until ENSEMBLE_PASSCODE is set. "
				+ "Existing accounts can still log in via POST /api/auth");
		}
	}

	@Bean
	SessionTokenService sessionTokenService(SecurityProperties properties, Clock clock) {
		return new SessionTokenService(properties, clock);
	}

	@Bean
	FilterRegistrationBean<SessionAuthFilter> sessionAuthFilterRegistration(SessionTokenService tokenService) {
		FilterRegistrationBean<SessionAuthFilter> registration = new FilterRegistrationBean<>(
			new SessionAuthFilter(tokenService));
		registration.addUrlPatterns("/api/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
