package io.github.minunglee.gateway.llm;

import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigDisabled;

/**
 * Anthropic 공식 Java SDK로 Claude를 호출한다.
 */
public class AnthropicLlmClient implements LlmClient {

	private final AnthropicClient client;

	public AnthropicLlmClient(AnthropicClient client) {
		this.client = client;
	}

	/** Claude 호출. 거절(refusal)이면 안내 문구, 답변이 길이 한도에서 잘렸으면 표시를 붙인다 (시스템 프롬프트로 형식을 정한 내부 요청은 제외) */
	@Override
	public LlmResponse complete(LlmRequest request) {
		MessageCreateParams.Builder builder = MessageCreateParams.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			// 데모는 짧은 질의응답이라 사고 깊이를 낮춰 비용과 지연을 줄인다
			.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
			.addUserMessage(request.userMessage());
		if (request.system() != null) {
			// 형식이 정해진 짧은 답(내부 API)은 생각에 토큰을 쓰면 답을 쓰기 전에 max_tokens가 끝나 빈 답이 온다.
			// (실측: 300토큰을 생각에 다 쓰고 본문 0자) 그래서 생각을 끈다
			builder.system(request.system()).thinking(ThinkingConfigDisabled.builder().build());
		}
		MessageCreateParams params = builder.build();

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
		// 데모는 답변 길이(max_tokens)를 제한하므로, 잘렸으면 알려준다.
		// 시스템 프롬프트로 출력 형식을 정한 요청(내부 API)은 덧붙이면 형식이 깨지므로 그대로 둔다
		if (request.system() == null && message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
			text = text + "\n\n(데모라 답변 길이를 제한해 여기까지만 표시합니다)";
		}

		return new LlmResponse(text, request.model(), message.usage().inputTokens(), message.usage().outputTokens());
	}

	/** 로그와 화면에 표시할 동작 모드 */
	@Override
	public String mode() {
		return "anthropic";
	}

}
