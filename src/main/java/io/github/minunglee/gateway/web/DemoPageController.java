package io.github.minunglee.gateway.web;

import io.github.minunglee.gateway.llm.LlmClient;
import io.github.minunglee.gateway.llm.LlmProperties;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoPageController {

	private final LlmClient llmClient;

	private final LlmProperties llmProperties;

	DemoPageController(LlmClient llmClient, LlmProperties llmProperties) {
		this.llmClient = llmClient;
		this.llmProperties = llmProperties;
	}

	@GetMapping("/")
	String index(Model model) {
		model.addAttribute("mode", llmClient.mode());
		model.addAttribute("model", llmProperties.model());
		return "index";
	}

}
