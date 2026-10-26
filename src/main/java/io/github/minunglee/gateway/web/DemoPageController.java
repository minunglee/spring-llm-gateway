package io.github.minunglee.gateway.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.bot.TurnstileVerifier;
import io.github.minunglee.gateway.invite.RequesterResolver;
import io.github.minunglee.gateway.limit.Requester;
import io.github.minunglee.gateway.llm.LlmClient;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoPageController {

	private final LlmClient llmClient;

	private final RequesterResolver requesterResolver;

	private final TurnstileVerifier turnstileVerifier;

	DemoPageController(LlmClient llmClient, RequesterResolver requesterResolver,
			TurnstileVerifier turnstileVerifier) {
		this.llmClient = llmClient;
		this.requesterResolver = requesterResolver;
		this.turnstileVerifier = turnstileVerifier;
	}

	/** GET /gateway: 게이트웨이 소개와 체험 화면. 게스트에게만 봇 확인 위젯을 보여준다 */
	@GetMapping("/gateway")
	String gateway(Model model, HttpServletRequest request) {
		model.addAttribute("mode", llmClient.mode());
		Requester requester = requesterResolver.resolve(request);
		model.addAttribute("requester", requester);
		// 게스트에게만 봇 확인 위젯을 보여준다
		model.addAttribute("turnstileSiteKey",
				turnstileVerifier.enabled() && !requester.invited() ? turnstileVerifier.siteKey() : null);
		return "gateway";
	}

}
