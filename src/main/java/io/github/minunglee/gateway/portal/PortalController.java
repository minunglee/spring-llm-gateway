package io.github.minunglee.gateway.portal;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.RequesterResolver;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 포트폴리오 메인 페이지 (https://minunglee.cloud).
 */
@Controller
class PortalController {

	private final RequesterResolver requesterResolver;

	PortalController(RequesterResolver requesterResolver) {
		this.requesterResolver = requesterResolver;
	}

	/**
	 * GET /: 포트폴리오 메인
	 *
	 * @param invite "invalid"면 초대 링크의 코드가 틀렸다는 안내를 보여준다
	 */
	@GetMapping("/")
	String index(Model model, HttpServletRequest request,
			@RequestParam(required = false) String invite) {
		model.addAttribute("requester", requesterResolver.resolve(request));
		// 초대 링크의 코드가 틀리거나 만료됐을 때 안내
		model.addAttribute("inviteInvalid", "invalid".equals(invite));
		return "portal";
	}

	/** 집돈 계산기(jipdon.com) 프로젝트 소개. 실제 서비스는 별도 도메인의 정적 사이트다 */
	@GetMapping("/jipdon")
	String jipdon() {
		return "jipdon";
	}

}
