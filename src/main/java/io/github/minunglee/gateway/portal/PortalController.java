package io.github.minunglee.gateway.portal;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.RequesterResolver;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 포트폴리오 메인 페이지 (https://minunglee.cloud).
 */
@Controller
class PortalController {

	private final RequesterResolver requesterResolver;

	PortalController(RequesterResolver requesterResolver) {
		this.requesterResolver = requesterResolver;
	}

	@GetMapping("/")
	String index(Model model, HttpServletRequest request) {
		model.addAttribute("requester", requesterResolver.resolve(request));
		return "portal";
	}

}
