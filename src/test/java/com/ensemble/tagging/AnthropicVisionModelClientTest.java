package com.ensemble.tagging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.services.blocking.MessageService;
import com.ensemble.config.AnthropicProperties;

/**
 * Verifies the SDK seam builds the correct forced-structured-output vision
 * request and extracts the tool-use JSON — all against a <strong>mocked</strong>
 * {@code AnthropicClient}, so no key and no network call are involved.
 */
class AnthropicVisionModelClientTest {

	private final AnthropicClient client = mock(AnthropicClient.class);
	private final MessageService messages = mock(MessageService.class);

	private AnthropicVisionModelClient seam() {
		when(client.messages()).thenReturn(messages);
		return new AnthropicVisionModelClient(
			client, new AnthropicProperties("claude-haiku-4-5", Duration.ofSeconds(30), null, null));
	}

	private ContentBlock toolUseContent(Map<String, Object> input) {
		ToolUseBlock toolUse = mock(ToolUseBlock.class);
		when(toolUse._input()).thenReturn(JsonValue.from(input));
		ContentBlock block = mock(ContentBlock.class);
		when(block.toolUse()).thenReturn(Optional.of(toolUse));
		return block;
	}

	private Message messageWithContent(List<ContentBlock> content) {
		Message message = mock(Message.class);
		when(message.content()).thenReturn(content);
		return message;
	}

	@Test
	void request_targetsHaiku_carriesImage_andForcesTagTool() {
		Message reply = messageWithContent(List.of(toolUseContent(Map.of("category", "top"))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		seam().extractTagsJson(new byte[]{1, 2, 3});

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(messages).create(captor.capture());
		MessageCreateParams params = captor.getValue();

		// Model is pinned to Haiku 4.5.
		assertThat(params.model().asString()).isEqualTo("claude-haiku-4-5");
		// Forced structured output: the tag tool is offered and forced via tool_choice.
		assertThat(params.tools()).isPresent();
		assertThat(params.toolChoice()).isPresent();
		assertThat(params.toolChoice().get().asTool().name())
			.isEqualTo(AnthropicVisionModelClient.TAG_TOOL);
		// The garment image travels in the user message.
		List<ContentBlockParam> blocks = params.messages().get(0).content().asBlockParams();
		assertThat(blocks).anyMatch(ContentBlockParam::isImage);
	}

	@Test
	void validToolUse_returnsInputJson() {
		Message reply = messageWithContent(List.of(toolUseContent(Map.of("category", "top", "formality", 3))));
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		String json = seam().extractTagsJson(new byte[]{1});

		assertThat(json).contains("\"category\":\"top\"").contains("\"formality\":3");
	}

	@Test
	void noToolUseBlock_returnsNull() {
		Message reply = messageWithContent(List.of());
		when(messages.create(any(MessageCreateParams.class))).thenReturn(reply);

		assertThat(seam().extractTagsJson(new byte[]{1})).isNull();
	}
}
