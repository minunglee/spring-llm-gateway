package io.github.minunglee.gateway.chat;

import io.github.minunglee.gateway.llm.LlmException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

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

	@ExceptionHandler(LlmException.class)
	ProblemDetail handleLlm(LlmException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.");
	}

}
