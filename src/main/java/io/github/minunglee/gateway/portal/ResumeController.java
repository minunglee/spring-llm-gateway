package io.github.minunglee.gateway.portal;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.InviteAccessLog;
import io.github.minunglee.gateway.invite.InviteService;
import io.github.minunglee.gateway.invite.RequesterResolver;
import io.github.minunglee.gateway.limit.Requester;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 이력서·경력기술서. 초대코드가 있어야 열람할 수 있고, 열람하면 코드별로 기록한다.
 */
@Controller
class ResumeController {

	private final RequesterResolver requesterResolver;

	private final InviteService inviteService;

	ResumeController(RequesterResolver requesterResolver, InviteService inviteService) {
		this.requesterResolver = requesterResolver;
		this.inviteService = inviteService;
	}

	@GetMapping("/resume")
	String resume(Model model, HttpServletRequest request) {
		Requester requester = requesterResolver.resolve(request);
		model.addAttribute("requester", requester);
		if (!requester.invited()) {
			return "resume-locked";
		}
		inviteService.log(requester.inviteCodeId(), InviteAccessLog.Action.RESUME_VIEW);
		return "resume";
	}

}
