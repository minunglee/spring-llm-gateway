package io.github.minunglee.gateway.llm;

import java.time.Duration;

/**
 * 비용 없이 개발·부하 테스트를 하기 위한 가짜 LLM. 지연시간과 토큰 사용량을 흉내 낸다.
 */
public class MockLlmClient implements LlmClient {

	private final Duration latency;

	public MockLlmClient(Duration latency) {
		this.latency = latency;
	}

	/** 설정한 지연만큼 기다린 뒤 질문을 되돌려주는 가짜 답과 추정 토큰 수를 돌려준다 */
	@Override
	public LlmResponse complete(LlmRequest request) {
		sleep();
		String text = "[가짜 응답] \"" + request.userMessage() + "\" 질문을 받았습니다. "
				+ "실제 Claude를 호출하지 않아 비용이 들지 않습니다.";
		return new LlmResponse(text, request.model(), estimateTokens(request.userMessage()), estimateTokens(text));
	}

	/** 로그와 화면에 표시할 동작 모드 */
	@Override
	public String mode() {
		return "mock";
	}

	/** 한국어 기준 대략 2글자당 1토큰으로 추정 */
	static long estimateTokens(String text) {
		return Math.max(1, text.length() / 2);
	}

	/** AI 응답 지연을 흉내 낸다 (부하 테스트에서 가상 스레드 효과를 재려고) */
	private void sleep() {
		try {
			Thread.sleep(latency);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LlmException("가짜 응답 생성이 중단되었습니다", ex);
		}
	}

}
