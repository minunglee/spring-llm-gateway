package io.github.minunglee.gateway.llm;

import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

/**
 * Anthropic 공식 Java SDK로 Claude를 호출한다.
 */
public class AnthropicLlmClient implements LlmClient {

	private final AnthropicClient client;

	public AnthropicLlmClient(AnthropicClient client) {
		this.client = client;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		MessageCreateParams params = MessageCreateParams.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			// 데모는 짧은 질의응답이라 사고 깊이를 낮춰 비용과 지연을 줄인다
			.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
			.addUserMessage(request.userMessage())
			.build();

		Message message;
		try {
			message = client.messages().create(params);
		}
		catch (AnthropicException ex) {
			throw new LlmException("Claude API 호출에 실패했습니다", ex);
		}

		String text = message.stopReason()
			.filter(StopReason.REFUSAL::equals)
			.map(reason -> "요청을 처리할 수 없는 내용입니다.")
			.orElseGet(() -> message.content()
				.stream()
				.flatMap(block -> block.text().stream())
				.map(textBlock -> textBlock.text())
				.collect(Collectors.joining()));

		return new LlmResponse(text, request.model(), message.usage().inputTokens(), message.usage().outputTokens());
	}

	@Override
	public String mode() {
		return "anthropic";
	}

}
