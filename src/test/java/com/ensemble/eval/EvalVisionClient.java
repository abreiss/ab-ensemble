package com.ensemble.eval;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.Usage;
import com.ensemble.tagging.AnthropicVisionModelClient;
import com.ensemble.tagging.TaggingService;
import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Issues one <strong>production-shape</strong> vision-tag request for a given model and captures
 * the parsed tags, token usage, and wall-clock latency. Reuses
 * {@link AnthropicVisionModelClient#tagRequestBuilder} (identical request as the live app) and
 * {@link TaggingService#map} (identical parse/clamp), so the only intended differences between
 * arms are the model id and the thinking config. Live-call glue — exercised by the manual run,
 * not the unit suite.
 */
final class EvalVisionClient {

	private final AnthropicClient client;

	EvalVisionClient(AnthropicClient client) {
		this.client = client;
	}

	/**
	 * Tags one already-resized JPEG with the given model. {@code disableThinking} sends an
	 * explicit {@code thinking: disabled} — used for Sonnet 5 (which otherwise runs adaptive
	 * thinking) so the comparison stays a single-shot perception call, matching Haiku's default.
	 */
	ModelRun run(String model, byte[] jpeg, boolean disableThinking) {
		MessageCreateParams.Builder builder = AnthropicVisionModelClient.tagRequestBuilder(model, jpeg);
		if (disableThinking) {
			builder.thinking(ThinkingConfigDisabled.builder().build());
		}

		long start = System.nanoTime();
		Message message = client.messages().create(builder.build());
		long latencyMs = (System.nanoTime() - start) / 1_000_000L;

		String rawJson = AnthropicVisionModelClient.firstToolUseJson(message);
		Usage usage = message.usage();

		boolean parseError = false;
		TagSuggestion tags;
		try {
			tags = TaggingService.map(rawJson);
		} catch (Exception e) {
			tags = TagSuggestion.empty();
			parseError = true;
		}

		return new ModelRun(model, rawJson, tags, latencyMs, usage.inputTokens(), usage.outputTokens(), parseError);
	}
}
