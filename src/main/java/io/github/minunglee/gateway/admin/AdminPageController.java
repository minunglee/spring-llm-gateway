package io.github.minunglee.gateway.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.minunglee.gateway.limit.ClientKeyResolver;
import io.github.minunglee.gateway.limit.RateLimiter;
import io.github.minunglee.gateway.visit.JipdonStatsClient;
import io.github.minunglee.gateway.visit.VisitService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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

	private final VisitService visitService;

	private final JipdonStatsClient jipdonStats;

	AdminPageController(AdminSession session, AdminDashboardService dashboardService, RateLimiter rateLimiter,
			ClientKeyResolver clientKeyResolver, VisitService visitService, JipdonStatsClient jipdonStats) {
		this.visitService = visitService;
		this.jipdonStats = jipdonStats;
		this.session = session;
		this.dashboardService = dashboardService;
		this.rateLimiter = rateLimiter;
		this.clientKeyResolver = clientKeyResolver;
	}

	@GetMapping({ "/admin", "/admin/" })
	String dashboard(Model model, HttpServletRequest request, @RequestParam(required = false) String error,
			@RequestParam(defaultValue = "7") String range) {
		if (!session.isLoggedIn(request)) {
			model.addAttribute("error", error);
			return "admin-login";
		}
		model.addAttribute("dashboard",
				dashboardService.dashboard(AdminDashboardService.UsageRange.of(range)));
		model.addAttribute("cloudVisits", visitService.stats(30));
		model.addAttribute("jipdonVisits", jipdonStats.fetch(30).orElse(null));
		return "admin";
	}

	/** 두 사이트(minunglee.cloud, jipdon.com)의 방문 통계 상세 */
	@GetMapping("/admin/visits")
	String visits(@RequestParam(defaultValue = "30") int days, Model model) {
		int range = Math.clamp(days, 1, 90);
		model.addAttribute("days", range);
		model.addAttribute("cloudVisits", visitService.stats(range));
		model.addAttribute("jipdonVisits", jipdonStats.fetch(range).orElse(null));
		return "admin-visits";
	}

	/** 회사(초대코드)별 상세 */
	@GetMapping("/admin/codes/{id}")
	String inviteDetail(@PathVariable long id, Model model) {
		return dashboardService.inviteDetail(id).map(detail -> {
			model.addAttribute("detail", detail);
			return "admin-invite";
		}).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** 열람 기록 전체 (페이지, 회사·행동 필터) */
	@GetMapping("/admin/access")
	String access(@RequestParam(required = false) Long code, @RequestParam(required = false) String action,
			@RequestParam(defaultValue = "1") int page, Model model) {
		model.addAttribute("access", dashboardService.accessPage(code, action, page));
		return "admin-access";
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
		// 이 브라우저는 방문 통계에서 뺀다 (포트폴리오 페이지에도 실리는 운영자 표시)
		response.addHeader(HttpHeaders.SET_COOKIE, session.issueOwner(request.isSecure()).toString());
		return "redirect:/admin";
	}

	@PostMapping("/admin/logout")
	String logout(HttpServletRequest request, HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, session.clear(request.isSecure()).toString());
		return "redirect:/admin";
	}

}
