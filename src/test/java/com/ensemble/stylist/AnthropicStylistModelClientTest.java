package com.ensemble.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.services.blocking.MessageService;
import com.ensemble.config.AnthropicProperties;

/**
 * Verifies the stylist SDK seam builds a correct Sonnet 5 tool-loop request —
 * offering {@code searchWardrobe} + {@code record_outfit}, answering the wardrobe
 * tool call with the supplied text, forcing structured output, and carrying
 * <strong>no image bytes</strong> — all against a <strong>mocked</strong>
 * {@code AnthropicClient}, so no key and no network are involved.
 */
class AnthropicStylistModelClientTest {

	private final AnthropicClient client = mock(AnthropicClient.class);
	private final MessageService messages = mock(MessageService.class);

	private AnthropicStylistModelClient seam() {
		when(client.messages()).thenReturn(messages);
		return new AnthropicStylistModelClient(
			client, new AnthropicProperties("claude-haiku-4-5", Duration.ofSeconds(30), null, "claude-sonnet-5"));
	}

	private ContentBlock toolUse(String name, String id, Map<String, Object> input) {
		ToolUseBlock block = mock(ToolUseBlock.class);
		when(block.name()).thenReturn(name);
		when(block.id()).thenReturn(id);
		when(block._input()).thenReturn(JsonValue.from(input));
		ContentBlock content = mock(ContentBlock.class);
		when(content.toolUse()).thenReturn(Optional.of(block));
		return content;
	}

	private ContentBlock textOnly() {
		ContentBlock content = mock(ContentBlock.class);
		when(content.toolUse()).thenReturn(Optional.empty());
		return content;
	}

	private Message message(List<ContentBlock> content) {
		Message message = mock(Message.class);
		when(message.content()).thenReturn(content);
		return message;
	}

	private static List<String> toolNames(MessageCreateParams params) {
		return params.tools().orElseThrow().stream()
			.map(ToolUnion::tool).filter(Optional::isPresent).map(Optional::get)
			.map(tool -> tool.name()).toList();
	}

	private static Tool toolNamed(MessageCreateParams params, String name) {
		return params.tools().orElseThrow().stream()
			.map(ToolUnion::tool).filter(Optional::isPresent).map(Optional::get)
			.filter(tool -> name.equals(tool.name())).findFirst().orElseThrow();
	}

	private static void assertNoImageBlocks(MessageCreateParams params) {
		for (MessageParam mp : params.messages()) {
			mp.content().blockParams()
				.ifPresent(blocks -> assertThat(blocks).noneMatch(ContentBlockParam::isImage));
		}
	}

	@Test
	void request_targetsSonnet_offersBothTools_withAutoChoice_andNoImageBytes() {
		Message reply = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("a", "b"), "reason", "navy layers"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		String json = seam().proposeOutfit("wardrobe tags here", List.of(StylistMessage.user("streetwear today")));

		assertThat(json).contains("\"a\"").contains("\"b\"").contains("navy layers");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		MessageCreateParams params = captor.getValue();
		assertThat(params.model().asString()).isEqualTo("claude-sonnet-5");
		assertThat(toolNames(params)).contains("searchWardrobe", "record_outfit");
		// First turn lets the model decide (auto), it is not forced yet.
		assertThat(params.toolChoice().orElseThrow().isAuto()).isTrue();
		assertNoImageBlocks(params);
	}

	@Test
	void searchWardrobeToolCall_isAnsweredWithWardrobeText_thenRecordOutfitReturned() {
		Message search = message(List.of(toolUse("searchWardrobe", "tu1", Map.of())));
		when(search.toParam()).thenReturn(
			MessageParam.builder().role(MessageParam.Role.ASSISTANT).content("searching").build());
		Message record = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("a"), "reason", "clean"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(search).thenReturn(record);

		String json = seam().proposeOutfit("WARDROBE-TEXT", List.of(StylistMessage.user("minimal")));

		assertThat(json).contains("\"a\"").contains("clean");
		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages, times(2)).create(captor.capture());
		// The second turn feeds the wardrobe back as a tool result.
		MessageCreateParams second = captor.getAllValues().get(1);
		boolean hasToolResult = second.messages().stream()
			.map(MessageParam::content)
			.filter(c -> c.blockParams().isPresent())
			.flatMap(c -> c.asBlockParams().stream())
			.anyMatch(ContentBlockParam::isToolResult);
		assertThat(hasToolResult).isTrue();
		captor.getAllValues().forEach(AnthropicStylistModelClientTest::assertNoImageBlocks);
	}

	@Test
	void repeatedSearchWardrobe_isBoundedByCap_thenForcesRecord() {
		Message search = message(List.of(toolUse("searchWardrobe", "tu1", Map.of())));
		when(search.toParam()).thenReturn(
			MessageParam.builder().role(MessageParam.Role.ASSISTANT).content("searching").build());
		Message record = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("a"), "reason", "capped"))));
		// The model never records on its own; it keeps calling searchWardrobe.
		when(messages.create(any(MessageCreateParams.class)))
			.thenReturn(search, search, search, search, record);

		String json = seam().proposeOutfit("WARDROBE", List.of(StylistMessage.user("loop")));

		assertThat(json).contains("capped");
		// Bounded: CONTINUATION_CAP auto turns, then exactly one forced call — no runaway loop.
		verify(messages, times(AnthropicStylistModelClient.CONTINUATION_CAP + 1))
			.create(any(MessageCreateParams.class));
	}

	@Test
	void repickConversation_carriesDifferentLookInstruction() {
		Message reply = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("b"), "reason", "bolder"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		// A conversation with a prior assistant turn == a pushback re-pick.
		seam().proposeOutfit("wardrobe tags", List.of(
			StylistMessage.user("streetwear today"),
			StylistMessage.assistant("chose a and b"),
			StylistMessage.user("too plain")));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		String system = captor.getValue().system().orElseThrow().asString();
		// The model is nudged to produce a different look than the previous one.
		assertThat(system).containsIgnoringCase("different");
	}

	@Test
	void firstTurnConversation_hasNoDifferentLookInstruction() {
		Message reply = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("a"), "reason", "clean"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		// No assistant turn yet == the first pick; the re-pick nudge must not fire.
		seam().proposeOutfit("wardrobe tags", List.of(StylistMessage.user("streetwear today")));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		String system = captor.getValue().system().orElseThrow().asString();
		assertThat(system).doesNotContainIgnoringCase("different outfit");
	}

	@Test
	void repickConversation_forwardsTextOnly_noImageBytes() {
		Message reply = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("b"), "reason", "bolder"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		seam().proposeOutfit("wardrobe tags", List.of(
			StylistMessage.user("streetwear today"),
			StylistMessage.assistant("chose a and b"),
			StylistMessage.user("too plain")));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		MessageCreateParams params = captor.getValue();
		assertNoImageBlocks(params);
		// Every forwarded turn is plain text content — structurally byte-free.
		for (MessageParam mp : params.messages()) {
			assertThat(mp.content().string()).isPresent();
		}
	}

	@Test
	void recordOutfitTool_requestsPerItemRationale_inSchemaPromptAndDescription() {
		Message reply = message(List.of(
			toolUse("record_outfit", "r1",
				Map.of("reason", "clean", "pieces", List.of(Map.of("itemId", "a", "rationale", "base"))))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		seam().proposeOutfit("wardrobe tags", List.of(StylistMessage.user("brunch")));

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		MessageCreateParams params = captor.getValue();

		// The forced tool's schema requires a `pieces` array carrying a per-item `rationale`.
		Tool record = toolNamed(params, "record_outfit");
		Map<String, JsonValue> schema = record.inputSchema()._additionalProperties();
		String properties = schema.get("properties").toString();
		assertThat(properties).contains("pieces").contains("rationale");
		assertThat(schema.get("required").toString()).contains("pieces");

		// The tool description and the system prompt both ask for a per-piece rationale.
		assertThat(record.description().orElseThrow()).containsIgnoringCase("rationale");
		assertThat(params.system().orElseThrow().asString()).containsIgnoringCase("rationale");
	}

	@Test
	void whenModelStopsWithoutRecording_finalCallForcesRecordOutfit() {
		Message chatter = message(List.of(textOnly()));
		Message record = message(List.of(
			toolUse("record_outfit", "r1", Map.of("itemIds", List.of("a"), "reason", "forced"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(chatter).thenReturn(record);

		String json = seam().proposeOutfit("WARDROBE", List.of(StylistMessage.user("anything")));

		assertThat(json).contains("forced");
		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages, times(2)).create(captor.capture());
		// The final call forces the record_outfit tool so structured output is guaranteed.
		MessageCreateParams last = captor.getAllValues().get(1);
		assertThat(last.toolChoice().orElseThrow().isTool()).isTrue();
		assertThat(last.toolChoice().orElseThrow().asTool().name()).isEqualTo("record_outfit");
	}
}
