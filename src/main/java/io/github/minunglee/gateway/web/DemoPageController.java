package io.github.minunglee.gateway.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.RequesterResolver;
import io.github.minunglee.gateway.llm.LlmClient;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoPageController {

	private final LlmClient llmClient;

	private final RequesterResolver requesterResolver;

	DemoPageController(LlmClient llmClient, RequesterResolver requesterResolver) {
		this.llmClient = llmClient;
		this.requesterResolver = requesterResolver;
	}

	@GetMapping("/gateway")
	String gateway(Model model, HttpServletRequest request) {
		model.addAttribute("mode", llmClient.mode());
		model.addAttribute("requester", requesterResolver.resolve(request));
		return "gateway";
	}

}
