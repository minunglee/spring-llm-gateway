package io.github.minunglee.gateway.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.minunglee.gateway.pricing.CostCalculator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 체험 페이지에서 호출하는 API.
 */
@RestController
class DemoChatController {

	private final ChatService chatService;

	DemoChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/demo/chat")
	DemoChatResponse chat(@Valid @RequestBody DemoChatRequest request) {
		ChatService.ChatResult result = chatService.chat(request.message().strip());
		return new DemoChatResponse(result.answer(), result.model(), result.mode(), result.inputTokens(),
				result.outputTokens(), CostCalculator.formatUsd(result.costMicroUsd()), result.latencyMs());
	}

	record DemoChatRequest(@NotBlank(message = "질문을 입력해주세요") @Size(max = 2000,
			message = "질문은 2,000자 이하로 입력해주세요") String message) {
	}

	record DemoChatResponse(String answer, String model, String mode, long inputTokens, long outputTokens,
			String cost, long latencyMs) {
	}

}
