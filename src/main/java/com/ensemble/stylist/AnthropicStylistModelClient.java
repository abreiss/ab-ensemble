package com.ensemble.stylist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.ensemble.config.AnthropicProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic-SDK implementation of {@link StylistModelClient}. Runs a Sonnet 5
 * tool-loop: it offers a {@code searchWardrobe} tool (answered with the supplied
 * wardrobe text — tags + wear-history, never image bytes) and a
 * {@code record_outfit} tool, lets the model reason with {@code tool_choice: auto},
 * and then <strong>forces</strong> {@code record_outfit} so the pick is always
 * returned as structured JSON.
 *
 * <p>The loop is bounded by {@link #CONTINUATION_CAP} to prevent an unbounded
 * back-and-forth. The SDK {@code AnthropicClient} is injected {@link Lazy} so the
 * API key is only required when a real style request runs — never at context
 * startup or in mocked tests. The guardrail (id-validation, one retry) lives in
 * {@link StylistService}; this class only speaks to the model.
 *
 * <p>On a stateless re-pick — the resent conversation already carries a prior
 * assistant turn — the {@link #REPICK_INSTRUCTION} is appended to the system
 * prompt so the model produces a <em>different</em> look. The nudge is derived
 * from the text-only conversation, so the byte-free guarantee is unchanged.
 */
@Component
public class AnthropicStylistModelClient implements StylistModelClient {

	/** Tool the model calls to read the wardrobe; also asserted by tests. */
	static final String SEARCH_TOOL = "searchWardrobe";

	/** Forced final tool; its input JSON is the {@code {reason, pieces:[{itemId, rationale}]}} pick. */
	static final String RECORD_TOOL = "record_outfit";

	/** Upper bound on model turns so a misbehaving loop cannot run forever. */
	static final int CONTINUATION_CAP = 4;

	private static final long MAX_TOKENS = 1024L;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String SYSTEM_PROMPT = """
		You are a personal stylist. Build one complete, wearable outfit using ONLY \
		the clothes the user owns. Call the searchWardrobe tool to see the wardrobe \
		(each item's id, tags, and wear-history). Decide which pieces — and how many — \
		make a coherent look; there are no fixed slots, so work with what is available. \
		Prefer pieces that have not been worn recently when it does not hurt the look. \
		When you are ready, call record_outfit with the chosen pieces, each with its \
		itemId (exactly as in the wardrobe) and a one-line rationale for that piece, \
		plus a short overall reason the outfit works. Never invent an itemId not in the wardrobe.""";

	/**
	 * Appended to the system prompt on a re-pick (the conversation already carries a
	 * prior assistant turn) so the model varies the look instead of repeating it.
	 */
	private static final String REPICK_INSTRUCTION = """
		The user has pushed back on your previous outfit. Produce a DIFFERENT outfit \
		from the previous look — change the combination of pieces — while addressing \
		the user's latest feedback. Do not repeat the same set of itemIds you just \
		suggested.""";

	private final AnthropicClient client;
	private final String model;

	public AnthropicStylistModelClient(@Lazy AnthropicClient client, AnthropicProperties props) {
		this.client = client;
		this.model = props.stylistModel();
	}

	@Override
	public String proposeOutfit(String wardrobeToolText, List<StylistMessage> conversation) {
		String system = systemPromptFor(conversation);
		List<MessageParam> messages = new ArrayList<>();
		for (StylistMessage turn : conversation) {
			messages.add(MessageParam.builder()
				.role(turn.role() == StylistMessage.Role.USER
					? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
				.content(turn.text())
				.build());
		}

		// Let the model read the wardrobe and reason, answering each searchWardrobe call,
		// until it records an outfit or we hit the continuation cap.
		for (int turn = 0; turn < CONTINUATION_CAP; turn++) {
			Message response = client.messages().create(autoParams(messages, system));

			String recorded = firstToolUseJson(response, RECORD_TOOL);
			if (recorded != null) {
				return recorded;
			}

			List<ContentBlockParam> toolResults = searchResults(response, wardrobeToolText);
			if (toolResults.isEmpty()) {
				// Model stopped without recording and without asking for the wardrobe.
				break;
			}
			messages.add(response.toParam());
			messages.add(MessageParam.builder()
				.role(MessageParam.Role.USER)
				.contentOfBlockParams(toolResults)
				.build());
		}

		// Force the structured pick so a grounded (or ungroundable) result always comes back.
		Message forced = client.messages().create(forcedParams(messages, system));
		return firstToolUseJson(forced, RECORD_TOOL);
	}

	/**
	 * The system prompt for this conversation. On a re-pick — signalled by a prior
	 * assistant turn in the (text-only) conversation — the {@link #REPICK_INSTRUCTION}
	 * is appended so the model varies the look; a first turn uses the base prompt.
	 *
	 * <p>This relies on an invariant held by {@code StylistService}: assistant turns
	 * enter the conversation <em>only</em> from the client-resent re-pick history. The
	 * grounding retry corrects with a user turn (never an assistant turn), so a
	 * first-pick retry is not misread as a pushback re-pick.
	 */
	private static String systemPromptFor(List<StylistMessage> conversation) {
		boolean isRepick = conversation.stream()
			.anyMatch(turn -> turn.role() == StylistMessage.Role.ASSISTANT);
		return isRepick ? SYSTEM_PROMPT + "\n\n" + REPICK_INSTRUCTION : SYSTEM_PROMPT;
	}

	private MessageCreateParams autoParams(List<MessageParam> messages, String system) {
		return baseParams(messages, system).toolChoice(ToolChoiceAuto.builder().build()).build();
	}

	private MessageCreateParams forcedParams(List<MessageParam> messages, String system) {
		return baseParams(messages, system)
			.toolChoice(ToolChoiceTool.builder().name(RECORD_TOOL).build()).build();
	}

	private MessageCreateParams.Builder baseParams(List<MessageParam> messages, String system) {
		return MessageCreateParams.builder()
			.model(model)
			.maxTokens(MAX_TOKENS)
			.system(system)
			.addTool(searchWardrobeTool())
			.addTool(recordOutfitTool())
			.messages(messages);
	}

	/** Builds a {@code searchWardrobe} tool result for each such call in the response. */
	private static List<ContentBlockParam> searchResults(Message response, String wardrobeToolText) {
		List<ContentBlockParam> results = new ArrayList<>();
		for (ContentBlock block : response.content()) {
			Optional<ToolUseBlock> toolUse = block.toolUse();
			if (toolUse.isPresent() && SEARCH_TOOL.equals(toolUse.get().name())) {
				results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
					.toolUseId(toolUse.get().id())
					.content(wardrobeToolText)
					.build()));
			}
		}
		return results;
	}

	/** Returns the first tool-use block named {@code toolName} as JSON text, or {@code null}. */
	private static String firstToolUseJson(Message message, String toolName) {
		for (ContentBlock block : message.content()) {
			Optional<ToolUseBlock> toolUse = block.toolUse();
			if (toolUse.isPresent() && toolName.equals(toolUse.get().name())) {
				return serialize(toolUse.get()._input());
			}
		}
		return null;
	}

	private static String serialize(JsonValue input) {
		try {
			return MAPPER.writeValueAsString(input.convert(Object.class));
		} catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
			return null;
		}
	}

	/** No-parameter tool: the model calls it to receive the wardrobe text. */
	private static Tool searchWardrobeTool() {
		Tool.InputSchema schema = Tool.InputSchema.builder()
			.type(JsonValue.from("object"))
			.putAdditionalProperty("properties", JsonValue.from(Map.of()))
			.build();
		return Tool.builder()
			.name(SEARCH_TOOL)
			.description("Return the user's whole wardrobe as text: each item's id, tags, and "
				+ "wear-history. No image data. Call this before choosing an outfit.")
			.inputSchema(schema)
			.build();
	}

	/**
	 * Forced-output tool. Its input schema is the {@code {reason, pieces:[{itemId,
	 * rationale}]}} pick shape: a {@code pieces} array where each entry pairs an
	 * owned {@code itemId} with a one-line {@code rationale}, plus a whole-look
	 * {@code reason}.
	 */
	private static Tool recordOutfitTool() {
		Map<String, Object> pieceSchema = Map.of(
			"type", "object",
			"properties", Map.of(
				"itemId", Map.of("type", "string"),
				"rationale", Map.of("type", "string")),
			"required", List.of("itemId", "rationale"));
		Tool.InputSchema schema = Tool.InputSchema.builder()
			.type(JsonValue.from("object"))
			.putAdditionalProperty("properties", JsonValue.from(Map.of(
				"pieces", Map.of("type", "array", "items", pieceSchema),
				"reason", Map.of("type", "string"))))
			.putAdditionalProperty("required", JsonValue.from(List.of("pieces", "reason")))
			.build();
		return Tool.builder()
			.name(RECORD_TOOL)
			.description("Record the chosen outfit. `pieces` is one entry per garment: its itemId "
				+ "(exactly as listed in the wardrobe) and a one-line rationale for why that piece "
				+ "belongs in the look. `reason` is a short overall note on why the outfit works.")
			.inputSchema(schema)
			.build();
	}
}
