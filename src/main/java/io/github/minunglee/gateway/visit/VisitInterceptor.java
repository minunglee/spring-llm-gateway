package io.github.minunglee.gateway.visit;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.minunglee.gateway.admin.AdminSession;
import io.github.minunglee.gateway.limit.ClientKeyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 화면 페이지를 정상(200)으로 보여준 뒤 방문을 기록한다. API·정적 파일·관리자 화면은 세지 않고,
 * 운영자 본인(관리자 로그인 상태)의 방문도 빼서 통계가 부풀지 않게 한다. 기록에 실패해도 화면에는 영향이 없다.
 */
@Component
class VisitInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(VisitInterceptor.class);

	static final Set<String> PAGES = Set.of("/", "/gateway", "/jipdon", "/resume");

	private final VisitService visits;

	private final ClientKeyResolver clientKeyResolver;

	private final AdminSession adminSession;

	VisitInterceptor(VisitService visits, ClientKeyResolver clientKeyResolver, AdminSession adminSession) {
		this.visits = visits;
		this.clientKeyResolver = clientKeyResolver;
		this.adminSession = adminSession;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		if (!"GET".equals(request.getMethod()) || response.getStatus() != 200 || ex != null
				|| !PAGES.contains(request.getRequestURI()) || adminSession.isLoggedIn(request)) {
			return;
		}
		try {
			visits.record(request.getRequestURI(), request.getHeader(HttpHeaders.REFERER),
					clientKeyResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
		}
		catch (RuntimeException failure) {
			log.warn("방문 기록 실패: {}", failure.toString());
		}
	}

}
