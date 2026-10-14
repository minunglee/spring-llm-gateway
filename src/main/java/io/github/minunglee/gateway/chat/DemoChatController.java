package io.github.minunglee.gateway.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.ClientKeyResolver;
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

	private final ClientKeyResolver clientKeyResolver;

	private final BudgetGuard budgetGuard;

	DemoChatController(ChatService chatService, ClientKeyResolver clientKeyResolver, BudgetGuard budgetGuard) {
		this.chatService = chatService;
		this.clientKeyResolver = clientKeyResolver;
		this.budgetGuard = budgetGuard;
	}

	@PostMapping("/demo/chat")
	DemoChatResponse chat(@Valid @RequestBody DemoChatRequest request, HttpServletRequest servletRequest) {
		ChatService.ChatResult result = chatService.chat(clientKeyResolver.resolve(servletRequest),
				request.message().strip());
		return new DemoChatResponse(result.answer(), result.model(), result.mode(), result.inputTokens(),
				result.outputTokens(), CostCalculator.formatUsd(result.costMicroUsd()), result.latencyMs(),
				result.remainingThisHour(), result.remainingToday());
	}

	/** 체험 페이지에 오늘 전체 예산 사용 현황을 보여준다 */
	@GetMapping("/demo/budget")
	BudgetResponse budget() {
		long used = budgetGuard.usedTodayMicroUsd();
		long limit = budgetGuard.limitMicroUsd();
		return new BudgetResponse(CostCalculator.formatUsd(used), CostCalculator.formatUsd(limit),
				Math.min(100.0, used * 100.0 / limit));
	}

	record DemoChatRequest(@NotBlank(message = "질문을 입력해주세요") @Size(max = 2000,
			message = "질문은 2,000자 이하로 입력해주세요") String message) {
	}

	record DemoChatResponse(String answer, String model, String mode, long inputTokens, long outputTokens,
			String cost, long latencyMs, int remainingThisHour, int remainingToday) {
	}

	record BudgetResponse(String used, String limit, double usedPercent) {
	}

}
