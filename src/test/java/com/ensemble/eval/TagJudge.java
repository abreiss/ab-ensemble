package com.ensemble.eval;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.Usage;
import com.ensemble.eval.JudgeResultParser.JudgeVerdict;
import com.ensemble.tagging.AnthropicVisionModelClient;
import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Opus 4.8 acting as a blind judge over two candidate tag sets for one garment photo. The two
 * sets are presented as "A" and "B" only — the caller randomizes which model is A vs B per image
 * and de-anonymizes afterward (see {@link JudgeTally}) — so the judge cannot favor a model by
 * name or position. The human gold tags are supplied as the reference truth. The judge returns a
 * forced structured verdict, parsed by the defensively-tested {@link JudgeResultParser}.
 *
 * <p>Live-call glue — exercised by the manual run, not the unit suite.
 */
final class TagJudge {

	static final String JUDGE_MODEL = "claude-opus-4-8";
	static final String VERDICT_TOOL = "record_verdict";
	private static final long MAX_TOKENS = 1024L;

	private final AnthropicClient client;

	TagJudge(AnthropicClient client) {
		this.client = client;
	}

	/** The judge's outcome plus its own token/latency cost. */
	record JudgeRun(JudgeVerdict verdict, long inputTokens, long outputTokens, long latencyMs, String rawJson) {
	}

	JudgeRun judge(byte[] jpeg, TagSuggestion setA, TagSuggestion setB, GoldTag gold) {
		String base64 = Base64.getEncoder().encodeToString(jpeg);
		String prompt = """
			You are judging two automated wardrobe taggers on the single garment in this image.
			Set A and Set B are candidate tags from two different vision models (identities hidden).
			The GOLD tags are a human's reference labels for this garment.

			Decide which set better describes the garment overall, judging against what you see in
			the image and using GOLD as the reference truth. Return your verdict with the %s tool:
			- winner: "A", "B", or "tie"
			- perField: for each field (category, primaryColor, secondaryColor, formality, pattern,
			  warmth, descriptors) which set is better ("A", "B", or "tie")
			- reason: one or two sentences justifying the overall winner

			GOLD:
			%s

			Set A:
			%s

			Set B:
			%s
			""".formatted(VERDICT_TOOL, renderGold(gold), renderTags(setA), renderTags(setB));

		MessageCreateParams params = MessageCreateParams.builder()
			.model(JUDGE_MODEL)
			.maxTokens(MAX_TOKENS)
			.addTool(verdictTool())
			.toolChoice(ToolChoiceTool.builder().name(VERDICT_TOOL).build())
			.addUserMessageOfBlockParams(List.of(
				ContentBlockParam.ofImage(ImageBlockParam.builder()
					.source(Base64ImageSource.builder()
						.mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
						.data(base64)
						.build())
					.build()),
				ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build())))
			.build();

		long start = System.nanoTime();
		Message message = client.messages().create(params);
		long latencyMs = (System.nanoTime() - start) / 1_000_000L;

		String rawJson = AnthropicVisionModelClient.firstToolUseJson(message);
		Usage usage = message.usage();
		JudgeVerdict verdict = JudgeResultParser.parse(rawJson);
		return new JudgeRun(verdict, usage.inputTokens(), usage.outputTokens(), latencyMs, rawJson);
	}

	private static Tool verdictTool() {
		Tool.InputSchema schema = Tool.InputSchema.builder()
			.type(JsonValue.from("object"))
			.putAdditionalProperty("properties", JsonValue.from(Map.of(
				"winner", Map.of("type", "string", "enum", List.of("A", "B", "tie")),
				"perField", Map.of("type", "object"),
				"reason", Map.of("type", "string"))))
			.putAdditionalProperty("required", JsonValue.from(List.of("winner", "reason")))
			.build();
		return Tool.builder()
			.name(VERDICT_TOOL)
			.description("Record a blind verdict comparing two candidate garment tag sets.")
			.inputSchema(schema)
			.build();
	}

	private static String renderGold(GoldTag g) {
		return "  category=%s primaryColor=%s secondaryColor=%s formality=%s pattern=%s warmth=%s descriptors=%s"
			.formatted(g.category(), g.primaryColor(), g.secondaryColor(), g.formality(), g.pattern(), g.warmth(),
				g.descriptors());
	}

	private static String renderTags(TagSuggestion t) {
		return "  category=%s primaryColor=%s secondaryColor=%s formality=%s pattern=%s warmth=%s descriptors=%s"
			.formatted(t.category(), t.primaryColor(), t.secondaryColor(), t.formality(), t.pattern(), t.warmth(),
				t.descriptors());
	}
}
