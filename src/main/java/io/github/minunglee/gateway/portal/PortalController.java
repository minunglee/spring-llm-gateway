package io.github.minunglee.gateway.portal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 포트폴리오 메인 페이지 (https://minunglee.cloud).
 */
@Controller
class PortalController {

	@GetMapping("/")
	String index() {
		return "portal";
	}

}
