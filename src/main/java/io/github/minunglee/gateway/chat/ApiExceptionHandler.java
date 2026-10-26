package io.github.minunglee.gateway.chat;

import io.github.minunglee.gateway.limit.RateLimitExceededException;
import io.github.minunglee.gateway.llm.LlmException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	/** 입력 검사 실패 → 400, 첫 번째 오류 메시지를 그대로 보여준다 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(error -> error.getDefaultMessage())
			.orElse("잘못된 요청입니다");
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
	}

	/** 처리 도중 막힌 요청: 원래 오류 응답에 처리 단계(trace)를 덧붙인다 */
	@ExceptionHandler(TracedChatException.class)
	ResponseEntity<ProblemDetail> handleTraced(TracedChatException ex) {
		ResponseEntity<ProblemDetail> response = switch (ex.getCause()) {
			case RateLimitExceededException cause -> handleRateLimit(cause);
			case BotCheckFailedException cause -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(handleBotCheck(cause));
			case LlmException cause -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(handleLlm(cause));
			default -> ResponseEntity.internalServerError()
				.body(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		};
		response.getBody().setProperty("trace", ex.trace().steps());
		return response;
	}

	/** 사용량 초과 → 429 + Retry-After(초), 오늘 남은 횟수 */
	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
		// 내림하면 클라이언트가 조금 일찍 재시도해서 다시 막히므로 올림한다
		long retryAfterSeconds = Math.max(1, (ex.result().retryAfter().toMillis() + 999) / 1000);
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
		body.setProperty("retryAfterSeconds", retryAfterSeconds);
		body.setProperty("remainingToday", ex.result().remainingToday());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
			.body(body);
	}

	/** 봇 확인 실패 → 403 */
	@ExceptionHandler(BotCheckFailedException.class)
	ProblemDetail handleBotCheck(BotCheckFailedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	/** AI 호출 실패 → 502 (선차감한 예산은 ChatService가 이미 돌려줬다) */
	@ExceptionHandler(LlmException.class)
	ProblemDetail handleLlm(LlmException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.");
	}

}
