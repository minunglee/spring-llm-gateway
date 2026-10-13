package io.github.minunglee.gateway.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import io.github.minunglee.gateway.llm.LlmClient;
import io.github.minunglee.gateway.llm.LlmException;
import io.github.minunglee.gateway.llm.LlmProperties;
import io.github.minunglee.gateway.llm.LlmRequest;
import io.github.minunglee.gateway.llm.LlmResponse;
import io.github.minunglee.gateway.log.RequestLog;
import io.github.minunglee.gateway.log.RequestLogRepository;
import io.github.minunglee.gateway.pricing.CostCalculator;

import org.springframework.stereotype.Service;

/**
 * 요청 1건의 흐름: LLM 호출 → 비용 계산 → 기록.
 * 이후 단계에서 한도·예산 검사, 캐시, 장애 대응이 이 앞뒤로 붙는다.
 */
@Service
public class ChatService {

	private final LlmClient llmClient;

	private final LlmProperties llmProperties;

	private final CostCalculator costCalculator;

	private final RequestLogRepository requestLogRepository;

	private final Clock clock;

	public ChatService(LlmClient llmClient, LlmProperties llmProperties, CostCalculator costCalculator,
			RequestLogRepository requestLogRepository) {
		this.llmClient = llmClient;
		this.llmProperties = llmProperties;
		this.costCalculator = costCalculator;
		this.requestLogRepository = requestLogRepository;
		this.clock = Clock.systemUTC();
	}

	public ChatResult chat(String message) {
		Instant startedAt = clock.instant();
		LlmRequest request = new LlmRequest(llmProperties.model(), message, llmProperties.maxTokens());
		LlmResponse response;
		try {
			response = llmClient.complete(request);
		}
		catch (LlmException ex) {
			requestLogRepository.save(new RequestLog(llmClient.mode(), request.model(), RequestLog.Status.ERROR, 0,
					0, 0, elapsedMs(startedAt), startedAt));
			throw ex;
		}

		long costMicroUsd = costCalculator.costMicroUsd(response.model(), response.inputTokens(),
				response.outputTokens());
		long latencyMs = elapsedMs(startedAt);
		requestLogRepository.save(new RequestLog(llmClient.mode(), response.model(), RequestLog.Status.SUCCESS,
				response.inputTokens(), response.outputTokens(), costMicroUsd, latencyMs, startedAt));

		return new ChatResult(response.text(), response.model(), llmClient.mode(), response.inputTokens(),
				response.outputTokens(), costMicroUsd, latencyMs);
	}

	private long elapsedMs(Instant startedAt) {
		return Duration.between(startedAt, clock.instant()).toMillis();
	}

	public record ChatResult(String answer, String model, String mode, long inputTokens, long outputTokens,
			long costMicroUsd, long latencyMs) {
	}

}
