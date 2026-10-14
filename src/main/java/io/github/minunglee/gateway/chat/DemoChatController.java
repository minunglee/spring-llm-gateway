package io.github.minunglee.gateway.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.bot.TurnstileVerifier;
import io.github.minunglee.gateway.invite.RequesterResolver;
import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.Requester;
import io.github.minunglee.gateway.pricing.CostCalculator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 체험 페이지에서 호출하는 API.
 */
@RestController
class DemoChatController {

	private final ChatService chatService;

	private final RequesterResolver requesterResolver;

	private final BudgetGuard budgetGuard;

	private final TurnstileVerifier turnstileVerifier;

	DemoChatController(ChatService chatService, RequesterResolver requesterResolver, BudgetGuard budgetGuard,
			TurnstileVerifier turnstileVerifier) {
		this.chatService = chatService;
		this.requesterResolver = requesterResolver;
		this.budgetGuard = budgetGuard;
		this.turnstileVerifier = turnstileVerifier;
	}

	@PostMapping("/demo/chat")
	DemoChatResponse chat(@Valid @RequestBody DemoChatRequest request, HttpServletRequest servletRequest) {
		Requester requester = requesterResolver.resolve(servletRequest);
		// 게스트만 봇 확인을 한다. 초대코드 사용자는 코드로 이미 확인된 사람이다
		if (!requester.invited()
				&& !turnstileVerifier.verify(request.turnstileToken(), servletRequest.getRemoteAddr())) {
			throw new BotCheckFailedException();
		}
		ChatService.ChatResult result = chatService.chat(requester, request.message().strip());
		return new DemoChatResponse(result.answer(), result.model(), result.mode(), result.inputTokens(),
				result.outputTokens(), CostCalculator.formatUsd(result.costMicroUsd()), result.latencyMs(),
				result.remainingThisHour(), result.remainingToday());
	}

	/** 체험 페이지에 방문자 등급의 오늘 예산 사용 현황을 보여준다 */
	@GetMapping("/demo/budget")
	BudgetResponse budget(HttpServletRequest servletRequest) {
		Requester requester = requesterResolver.resolve(servletRequest);
		long used = budgetGuard.usedTodayMicroUsd(requester.tier());
		long limit = budgetGuard.limitMicroUsd(requester.tier());
		return new BudgetResponse(CostCalculator.formatUsd(used), CostCalculator.formatUsd(limit),
				Math.min(100.0, used * 100.0 / limit));
	}

	/**
	 * @param turnstileToken 봇 확인 위젯이 발급한 일회용 토큰 (게스트만 필요)
	 */
	record DemoChatRequest(@NotBlank(message = "질문을 입력해주세요") @Size(max = 2000,
			message = "질문은 2,000자 이하로 입력해주세요") String message, @Size(max = 4096) String turnstileToken) {
	}

	record DemoChatResponse(String answer, String model, String mode, long inputTokens, long outputTokens,
			String cost, long latencyMs, int remainingThisHour, int remainingToday) {
	}

	record BudgetResponse(String used, String limit, double usedPercent) {
	}

}
