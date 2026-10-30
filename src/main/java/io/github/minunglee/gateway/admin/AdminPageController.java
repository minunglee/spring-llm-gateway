package io.github.minunglee.gateway.admin;

import java.time.Clock;

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

	/** 같은 IP의 하루 로그인 시도 한도 (시간당 5회와 함께 무작위 대입을 막는다) */
	private static final int LOGIN_DAILY = 20;

	private final AdminSession session;

	private final AdminDashboardService dashboardService;

	private final RateLimiter rateLimiter;

	private final ClientKeyResolver clientKeyResolver;

	private final VisitService visitService;

	private final JipdonStatsClient jipdonStats;

	private final Clock clock;

	AdminPageController(AdminSession session, AdminDashboardService dashboardService, RateLimiter rateLimiter,
			ClientKeyResolver clientKeyResolver, VisitService visitService, JipdonStatsClient jipdonStats,
			Clock clock) {
		this.visitService = visitService;
		this.jipdonStats = jipdonStats;
		this.clock = clock;
		this.session = session;
		this.dashboardService = dashboardService;
		this.rateLimiter = rateLimiter;
		this.clientKeyResolver = clientKeyResolver;
	}

	/**
	 * GET /admin: 로그인돼 있으면 대시보드, 아니면 로그인 화면
	 *
	 * @param range 사용량 표 기간 (7, 30, all)
	 * @param q 초대코드 이름 검색어
	 * @param visited 접속한 초대코드만 보기
	 * @param invitePage 초대코드 표 페이지
	 */
	@GetMapping({ "/admin", "/admin/" })
	String dashboard(Model model, HttpServletRequest request, @RequestParam(required = false) String error,
			@RequestParam(defaultValue = "7") String range, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean visited, @RequestParam(defaultValue = "1") int invitePage) {
		if (!session.isLoggedIn(request)) {
			model.addAttribute("error", error);
			return "admin-login";
		}
		model.addAttribute("dashboard",
				dashboardService.dashboard(AdminDashboardService.UsageRange.of(range),
						new AdminDashboardService.InviteFilter(q, visited, invitePage)));
		model.addAttribute("cloudVisits", visitService.stats(30));
		model.addAttribute("jipdonVisits", jipdonStats.fetch(30).orElse(null));
		jipdonStats.fetchCollect().ifPresent(collect -> {
			model.addAttribute("jipdonCollect", collect);
			model.addAttribute("jipdonCollectHealth", collect.health(clock.instant()));
		});
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

	/** POST /admin/login: 시도 횟수를 먼저 제한하고, 토큰이 맞으면 관리자 쿠키(12시간)와 운영자 표시 쿠키(방문 통계 제외용)를 심는다 */
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

	/** POST /admin/logout: 관리자 쿠키만 지운다 (운영자 표시 쿠키는 남겨 이 브라우저는 계속 통계에서 빠진다) */
	@PostMapping("/admin/logout")
	String logout(HttpServletRequest request, HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, session.clear(request.isSecure()).toString());
		return "redirect:/admin";
	}

}
