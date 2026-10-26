package io.github.minunglee.gateway.admin;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /admin/** 는 본인만 쓴다. Bearer 토큰(스크립트·CLI용) 또는 로그인 쿠키(대시보드용)가 있어야 통과한다.
 * <p>
 * 대시보드 첫 화면(GET /admin), 로그인·로그아웃은 컨트롤러가 직접 처리하므로 통과시킨다 (로그아웃은 쿠키 삭제뿐이라 무해).
 * 관리자 토큰이 설정되지 않았으면 관리자 기능 전체를 숨긴다(404).
 */
class AdminTokenFilter extends OncePerRequestFilter {

	private final AdminSession session;

	AdminTokenFilter(AdminSession session) {
		this.session = session;
	}

	/** 관리자 토큰이 없으면 404, 공개 입구·Bearer 토큰·로그인 쿠키 중 하나면 통과, 아니면 401 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!session.enabled()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		if (isPublicEntry(request) || hasBearerToken(request) || session.isLoggedIn(request)) {
			chain.doFilter(request, response);
			return;
		}
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
	}

	/** 인증 없이 통과시키는 주소: 대시보드 첫 화면(로그인 화면을 보여줌), 로그인, 로그아웃 */
	private boolean isPublicEntry(HttpServletRequest request) {
		String path = request.getRequestURI();
		return ("GET".equals(request.getMethod()) && ("/admin".equals(path) || "/admin/".equals(path)))
				|| ("POST".equals(request.getMethod())
						&& ("/admin/login".equals(path) || "/admin/logout".equals(path)));
	}

	/** Authorization: Bearer 관리자토큰 헤더가 있는지 (curl 등 스크립트용) */
	private boolean hasBearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return header != null && header.startsWith("Bearer ") && session.matchesToken(header.substring(7));
	}

}
