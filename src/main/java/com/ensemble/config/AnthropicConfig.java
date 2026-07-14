package com.ensemble.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Wires the Anthropic SDK client used for vision tagging. The client reads its
 * API key from the {@code ANTHROPIC_API_KEY} environment variable
 * ({@code fromEnv()}) — the key is never a config property and never committed.
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
		return AnthropicOkHttpClient.builder()
			.fromEnv()
			.timeout(props.timeout())
			.build();
	}
}
