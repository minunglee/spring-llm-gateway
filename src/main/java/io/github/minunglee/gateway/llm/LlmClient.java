package io.github.minunglee.gateway.llm;

/**
 * LLM 호출 추상화. 실제 Claude 호출과 가짜 응답을 같은 인터페이스로 다룬다.
 */
public interface LlmClient {

	LlmResponse complete(LlmRequest request);

	/** 로그와 화면에 표시할 동작 모드 (mock / anthropic) */
	String mode();

}
