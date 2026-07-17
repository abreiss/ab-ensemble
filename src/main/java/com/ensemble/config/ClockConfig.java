package com.ensemble.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single {@link Clock} bean the app depends on for time-based decisions (session
 * token expiry, the daily-cap UTC day boundary) — {@link Clock#systemUTC()} in
 * production, fixed to a specific instant in tests so expiry/UTC-boundary logic is
 * deterministic.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
