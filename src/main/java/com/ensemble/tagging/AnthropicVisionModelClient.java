package com.ensemble.tagging;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.ToolUseBlock;
import com.ensemble.config.AnthropicProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic-SDK implementation of {@link VisionModelClient}. Builds a single
 * vision request that carries the garment image and <strong>forces</strong> a
 * structured tool call ({@value #TAG_TOOL}), so the model must return the tag
 * fields as tool input JSON rather than free-form prose. The raw tool input is
 * handed back as a JSON string for {@code TaggingService} to parse defensively.
 *
 * <p>The SDK {@code AnthropicClient} is injected {@link Lazy} so the API key is
 * only required when a real tag request runs — never at context startup or in
 * mocked tests.
 */
@Component
public class AnthropicVisionModelClient implements VisionModelClient {

	/** Forced tool name; also asserted by tests as the structured-output contract. */
	static final String TAG_TOOL = "extract_garment_tags";

	private static final long MAX_TOKENS = 1024L;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final AnthropicClient client;
	private final String model;

	public AnthropicVisionModelClient(@Lazy AnthropicClient client, AnthropicProperties props) {
		this.client = client;
		this.model = props.model();
	}

	@Override
	public String extractTagsJson(byte[] jpegImage) {
		String base64 = Base64.getEncoder().encodeToString(jpegImage);
		MessageCreateParams params = MessageCreateParams.builder()
			.model(model)
			.maxTokens(MAX_TOKENS)
			.addTool(tagTool())
			.toolChoice(ToolChoiceTool.builder().name(TAG_TOOL).build())
			.addUserMessageOfBlockParams(List.of(
				ContentBlockParam.ofImage(ImageBlockParam.builder()
					.source(Base64ImageSource.builder()
						.mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
						.data(base64)
						.build())
					.build()),
				ContentBlockParam.ofText(TextBlockParam.builder()
					.text("Tag this single garment using the " + TAG_TOOL + " tool. "
						+ "Leave any field you cannot determine from the image as null.")
					.build())))
			.build();

		Message message = client.messages().create(params);
		return firstToolUseJson(message);
	}

	/** Tool whose input schema is the tag shape; forcing it yields structured JSON. */
	private Tool tagTool() {
		Tool.InputSchema schema = Tool.InputSchema.builder()
			.type(JsonValue.from("object"))
			.putAdditionalProperty("properties", JsonValue.from(Map.of(
				"category", Map.of("type", "string"),
				"primaryColor", Map.of("type", "string"),
				"secondaryColor", Map.of("type", "string"),
				"formality", Map.of("type", "integer"),
				"pattern", Map.of("type", "string"),
				"warmth", Map.of("type", "integer"),
				"descriptors", Map.of("type", "array", "items", Map.of("type", "string")))))
			.build();
		return Tool.builder()
			.name(TAG_TOOL)
			.description("Extract structured wardrobe tags for the garment shown in the image.")
			.inputSchema(schema)
			.build();
	}

	/** Returns the first tool-use block's input as JSON text, or {@code null} if none. */
	private String firstToolUseJson(Message message) {
		for (ContentBlock block : message.content()) {
			Optional<ToolUseBlock> toolUse = block.toolUse();
			if (toolUse.isPresent()) {
				return serialize(toolUse.get()._input());
			}
		}
		return null;
	}

	private String serialize(JsonValue input) {
		try {
			return MAPPER.writeValueAsString(input.convert(Object.class));
		} catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
			return null;
		}
	}
}
