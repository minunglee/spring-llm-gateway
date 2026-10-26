package io.github.minunglee.gateway.portal;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.InviteAccessLog;
import io.github.minunglee.gateway.invite.InviteService;
import io.github.minunglee.gateway.invite.RequesterResolver;
import io.github.minunglee.gateway.limit.Requester;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 이력서·경력기술서. 초대코드가 있어야 열람할 수 있고, 열람·다운로드하면 코드별로 기록한다.
 */
@Controller
class ResumeController {

	private final RequesterResolver requesterResolver;

	private final InviteService inviteService;

	private final ResumeStore resumeStore;

	ResumeController(RequesterResolver requesterResolver, InviteService inviteService, ResumeStore resumeStore) {
		this.requesterResolver = requesterResolver;
		this.inviteService = inviteService;
		this.resumeStore = resumeStore;
	}

	/** GET /resume: 초대코드가 있으면 이력서를 보여주고 열람을 기록, 없으면 잠금 화면 */
	@GetMapping("/resume")
	String resume(Model model, HttpServletRequest request) {
		Requester requester = requesterResolver.resolve(request);
		model.addAttribute("requester", requester);
		if (!requester.invited()) {
			return "resume-locked";
		}
		inviteService.log(requester.inviteCodeId(), InviteAccessLog.Action.RESUME_VIEW);
		model.addAttribute("resume", resumeStore.resume().orElse(null));
		model.addAttribute("hasPdf", resumeStore.pdf().isPresent());
		return "resume";
	}

	/** GET /resume/pdf: 초대코드가 있으면 PDF를 내려주고 다운로드를 기록, 없으면 /resume 로 보낸다 */
	@GetMapping("/resume/pdf")
	ResponseEntity<Resource> pdf(HttpServletRequest request) {
		Requester requester = requesterResolver.resolve(request);
		if (!requester.invited()) {
			return ResponseEntity.status(303).header(HttpHeaders.LOCATION, "/resume").build();
		}
		return resumeStore.pdf().<ResponseEntity<Resource>>map(file -> {
			inviteService.log(requester.inviteCodeId(), InviteAccessLog.Action.RESUME_PDF);
			ContentDisposition disposition = ContentDisposition.attachment()
				.filename("이민웅_이력서_경력기술서.pdf", StandardCharsets.UTF_8)
				.build();
			return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				// 개인정보가 담긴 파일이라 브라우저·프록시에 남기지 않는다
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new FileSystemResource(file));
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
