package com.ensemble.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Wires the Anthropic SDK client used for vision tagging. The API key is supplied via
 * the {@code ENSEMBLE_ANTHROPIC_API_KEY} variable, read from a git-ignored {@code .env}
 * file (imported by {@code application.yml}) or the process environment, and bound onto
 * {@link AnthropicProperties}. When that value is present it is passed to the SDK
 * explicitly; when it is blank/unset the client falls back to the SDK's own environment
 * resolution ({@code fromEnv()}, i.e. the standard {@code ANTHROPIC_API_KEY}), so the key
 * still comes from the environment and is never a committed value.
 *
 * <p>The bean is {@link Lazy}: it is constructed only on first use (a real tag
 * request), not at context startup. This keeps {@code @SpringBootTest} context
 * loads and the mocked test slices runnable with <strong>no key set</strong> and
 * with no network call — mirroring the lazy-client stance of {@code DynamoDbConfig}.
 */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicConfig {

	@Bean
	@Lazy
	AnthropicClient anthropicClient(AnthropicProperties props) {
		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
			.timeout(props.timeout());
		if (props.apiKey() != null) {
			builder.apiKey(props.apiKey());
		} else {
			// No app-specific key configured — defer to the SDK's ANTHROPIC_API_KEY resolution.
			builder.fromEnv();
		}
		return builder.build();
	}
}
