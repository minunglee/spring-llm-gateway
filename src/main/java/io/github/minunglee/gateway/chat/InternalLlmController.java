package io.github.minunglee.gateway.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.limit.LimitProperties;
import io.github.minunglee.gateway.limit.Requester;
import io.github.minunglee.gateway.limit.Tier;
import io.github.minunglee.gateway.llm.LlmRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 같은 서버의 다른 서비스(jipdon.com)가 AI를 부를 때 쓰는 내부 API.
 * <p>
 * 체험 화면과 같은 단계(사용량 제한 → 긴급 차단 → 예산 선차감 → 호출 → 정산)를 거치되, 예산과 한도는 서비스 몫(JIPDON 등급)을
 * 따로 쓴다. 프롬프트와 결과 검증은 그 서비스가 맡고, 게이트웨이는 비용과 남용만 지킨다.
 * Nginx가 바깥에서 /internal/ 을 막고, 토큰이 틀리면 주소가 없는 것처럼 404를 준다.
 */
@RestController
class InternalLlmController {

	/** 내부 API로 받을 수 있는 최대 답변 길이 (선차감 금액이 이 값에 비례한다) */
	static final int MAX_TOKENS = 512;

	private final ChatService chatService;

	private final LimitProperties limitProperties;

	private final byte[] jipdonToken;

	private final Validator validator;

	InternalLlmController(ChatService chatService, LimitProperties limitProperties, Validator validator,
			@Value("${gateway.internal.jipdon-token:}") String jipdonToken) {
		this.chatService = chatService;
		this.limitProperties = limitProperties;
		this.validator = validator;
		this.jipdonToken = jipdonToken.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @param subject 그 서비스가 최종 사용자를 구분하는 키 (원래 IP가 아닌 해시). 사용량 제한을 이 키로 센다
	 * @param system 시스템 프롬프트
	 * @param message 사용자 질문
	 * @param maxTokens 답변 최대 길이
	 */
	record InternalLlmRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String subject,
			@Size(max = 8000) String system, @NotBlank @Size(max = 1000) String message,
			@Min(16) @Max(MAX_TOKENS) int maxTokens) {
	}

	/**
	 * @param degraded 예산 소진·긴급 차단 등으로 AI를 부르지 않았으면 true (answer는 안내 문구)
	 */
	record InternalLlmResponse(String answer, String model, String mode, boolean degraded, long inputTokens,
			long outputTokens, int remainingToday) {
	}

	@PostMapping("/internal/llm")
	ResponseEntity<?> complete(@RequestHeader(value = "X-Internal-Token", required = false) String token,
			@RequestBody InternalLlmRequest request) {
		// 토큰부터 본다. 입력 검사를 먼저 하면 토큰 없는 요청에도 400이 나가 주소가 있다는 걸 알려준다
		if (!matches(token)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			var first = violations.iterator().next();
			return ResponseEntity.badRequest()
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
						first.getPropertyPath() + ": " + first.getMessage()));
		}
		LimitProperties.TierPolicy policy = limitProperties.jipdon();
		Requester requester = new Requester(Tier.JIPDON, "jipdon:" + request.subject(), null, null, policy.model(),
				policy.hourly(), policy.daily());
		LlmRequest llmRequest = new LlmRequest(policy.model(), request.message().strip(), request.maxTokens(),
				request.system());
		ChatService.ChatResult result = chatService.chat(requester, llmRequest, new Trace());
		return ResponseEntity.ok(new InternalLlmResponse(result.answer(), result.model(), result.mode(),
				"demo".equals(result.mode()), result.inputTokens(), result.outputTokens(), result.remainingToday()));
	}

	/** 공유 토큰이 설정돼 있고 요청 토큰과 같은지 (상수 시간 비교) */
	private boolean matches(String token) {
		return jipdonToken.length > 0 && token != null
				&& MessageDigest.isEqual(jipdonToken, token.getBytes(StandardCharsets.UTF_8));
	}

}
