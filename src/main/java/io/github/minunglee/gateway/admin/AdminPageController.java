package io.github.minunglee.gateway.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.minunglee.gateway.limit.ClientKeyResolver;
import io.github.minunglee.gateway.limit.RateLimiter;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 대시보드 (본인 전용). 로그인하지 않으면 로그인 화면만 보인다.
 */
@Controller
class AdminPageController {

	/** 관리자 토큰 무작위 대입 방지 */
	private static final int LOGIN_HOURLY = 5;

	private static final int LOGIN_DAILY = 20;

	private final AdminSession session;

	private final AdminDashboardService dashboardService;

	private final RateLimiter rateLimiter;

	private final ClientKeyResolver clientKeyResolver;

	AdminPageController(AdminSession session, AdminDashboardService dashboardService, RateLimiter rateLimiter,
			ClientKeyResolver clientKeyResolver) {
		this.session = session;
		this.dashboardService = dashboardService;
		this.rateLimiter = rateLimiter;
		this.clientKeyResolver = clientKeyResolver;
	}

	@GetMapping({ "/admin", "/admin/" })
	String dashboard(Model model, HttpServletRequest request, @RequestParam(required = false) String error) {
		if (!session.isLoggedIn(request)) {
			model.addAttribute("error", error);
			return "admin-login";
		}
		model.addAttribute("dashboard", dashboardService.dashboard());
		return "admin";
	}

	@PostMapping("/admin/login")
	String login(@RequestParam String token, HttpServletRequest request, HttpServletResponse response) {
		boolean allowed = rateLimiter
			.tryAcquire("admin-login:" + clientKeyResolver.resolve(request), LOGIN_HOURLY, LOGIN_DAILY)
			.allowed();
		if (!allowed) {
			return "redirect:/admin?error=locked";
		}
		if (!session.matchesToken(token)) {
			return "redirect:/admin?error=invalid";
		}
		response.addHeader(HttpHeaders.SET_COOKIE, session.issue(request.isSecure()).toString());
		return "redirect:/admin";
	}

	@PostMapping("/admin/logout")
	String logout(HttpServletRequest request, HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, session.clear(request.isSecure()).toString());
		return "redirect:/admin";
	}

}
