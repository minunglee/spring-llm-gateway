package io.github.minunglee.gateway.llm;

/**
 * @param system 시스템 프롬프트 (없으면 null). 내부 API로 다른 서비스가 역할과 출력 형식을 정할 때 쓴다
 */
public record LlmRequest(String model, String userMessage, long maxTokens, String system) {

	/** 시스템 프롬프트 없는 요청 (체험 화면) */
	public LlmRequest(String model, String userMessage, long maxTokens) {
		this(model, userMessage, maxTokens, null);
	}

}
