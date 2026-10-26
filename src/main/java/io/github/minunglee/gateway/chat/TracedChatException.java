package io.github.minunglee.gateway.chat;

/**
 * 처리 도중 막힌 요청. 원래 예외(사용량 초과, 봇 확인 실패, AI 호출 실패)와 그때까지의 처리 단계를 함께 담아,
 * 오류 응답에서도 어느 단계에서 왜 막혔는지 보여준다.
 */
class TracedChatException extends RuntimeException {

	private final Trace trace;

	TracedChatException(RuntimeException cause, Trace trace) {
		super(cause.getMessage(), cause);
		this.trace = trace;
	}

	/** 막히기 전까지 거친 단계 (오류 응답에 함께 실린다) */
	Trace trace() {
		return trace;
	}

}
