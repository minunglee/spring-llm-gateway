package io.github.minunglee.gateway.portal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 이력서·경력기술서. 초대코드 기능이 생기기 전까지는 잠금 안내만 보여준다.
 */
@Controller
class ResumeController {

	@GetMapping("/resume")
	String resume() {
		return "resume-locked";
	}

}
