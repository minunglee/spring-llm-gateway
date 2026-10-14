package io.github.minunglee.gateway.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /admin/** 요청에 Bearer 토큰을 요구한다. 토큰이 설정되지 않았으면 관리자 API 자체를 숨긴다(404).
 */
class AdminTokenFilter extends OncePerRequestFilter {

	private final byte[] expected;

	AdminTokenFilter(String token) {
		this.expected = StringUtils.hasText(token) ? ("Bearer " + token).getBytes(StandardCharsets.UTF_8) : null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (expected == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		// 비교 시간으로 토큰을 추측할 수 없도록 상수 시간 비교를 쓴다
		if (header == null || !MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8))) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		chain.doFilter(request, response);
	}

}
