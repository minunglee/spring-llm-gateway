package io.github.minunglee.gateway.admin;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.gateway.invite.InviteProperties;

import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

/**
 * 관리자(본인) 로그인 상태를 담는 서명 쿠키.
 * <p>
 * 관리자 토큰으로 로그인하면 12시간짜리 쿠키를 발급한다. SameSite=Strict라 다른 사이트에서 보낸 요청에는
 * 쿠키가 실리지 않아, 대시보드의 변경 요청(긴급 차단, 코드 발급·폐기)을 외부에서 위조할 수 없다.
 */
public class AdminSession {

	/** 관리자 로그인 쿠키 이름 */
	static final String COOKIE = "admin";

	/**
	 * 운영자 표시 쿠키. 관리자 쿠키는 /admin 에서만 실려, 포트폴리오 페이지에서는 운영자인지 알 수 없다.
	 * 방문 통계에서 본인 방문을 빼려고 사이트 전체(/)에 따로 심는다. 서명 문구가 달라 관리자 권한으로는 쓸 수 없다.
	 */
	static final String OWNER_COOKIE = "owner";

	/** 관리자 로그인 유지 시간. 길게 두면 쿠키가 새었을 때 위험이 커서 반나절로 둔다 */
	private static final Duration MAX_AGE = Duration.ofHours(12);

	/** 운영자 표시 쿠키 유지 시간 (권한이 없는 표시용이라 길게 둔다) */
	private static final Duration OWNER_MAX_AGE = Duration.ofDays(365);

	private final byte[] token;

	private final SecretKeySpec key;

	private final Clock clock;

	AdminSession(AdminProperties adminProperties, InviteProperties inviteProperties, Clock clock) {
		this.token = StringUtils.hasText(adminProperties.token())
				? adminProperties.token().getBytes(StandardCharsets.UTF_8) : null;
		// 초대코드 쿠키와 같은 비밀값을 쓰되 용도 문자열을 붙여 서명을 섞이지 않게 한다
		this.key = new SecretKeySpec((inviteProperties.cookieSecret() + ":admin").getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		this.clock = clock;
	}

	/** 관리자 토큰이 설정돼 있는지. 없으면 관리자 기능 전체를 숨긴다 */
	boolean enabled() {
		return token != null;
	}

	/** 입력한 토큰이 관리자 토큰과 같은지 (걸리는 시간으로 값을 추측할 수 없게 상수 시간 비교) */
	boolean matchesToken(String candidate) {
		return token != null && candidate != null
				&& MessageDigest.isEqual(token, candidate.strip().getBytes(StandardCharsets.UTF_8));
	}

	/** 관리자 로그인 쿠키 발급: 값은 "만료시각.서명", 12시간 유지 */
	ResponseCookie issue(boolean secure) {
		long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
		String payload = "admin." + expiresAt;
		return base(expiresAt + "." + sign(payload), secure).maxAge(MAX_AGE).build();
	}

	/** 관리자 로그인 쿠키를 지운다 (만료 시간 0) */
	ResponseCookie clear(boolean secure) {
		return base("", secure).maxAge(0).build();
	}

	/** 로그인할 때 함께 심는다. 로그아웃해도 남겨 두어 이 브라우저는 계속 통계에서 빠진다 */
	ResponseCookie issueOwner(boolean secure) {
		long expiresAt = clock.instant().plus(OWNER_MAX_AGE).getEpochSecond();
		return ResponseCookie.from(OWNER_COOKIE, expiresAt + "." + sign("owner." + expiresAt))
			.path("/")
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.maxAge(OWNER_MAX_AGE)
			.build();
	}

	/** 운영자 본인의 브라우저인지 (방문 통계 제외용, 권한 확인에는 쓰지 않는다) */
	public boolean isOwner(HttpServletRequest request) {
		if (!enabled() || request.getCookies() == null) {
			return false;
		}
		return Arrays.stream(request.getCookies())
			.filter(cookie -> OWNER_COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.anyMatch(value -> verify("owner.", value));
	}

	/** 서명이 맞고 만료되지 않은 관리자 쿠키가 있는지 */
	public boolean isLoggedIn(HttpServletRequest request) {
		if (!enabled() || request.getCookies() == null) {
			return false;
		}
		return Arrays.stream(request.getCookies())
			.filter(cookie -> COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.anyMatch(value -> verify("admin.", value));
	}

	/**
	 * "만료시각.서명" 쿠키 값 검증: 서명이 맞고(위조 아님) 아직 만료 전이어야 true
	 *
	 * @param purpose 서명에 섞는 용도 문자열 ("admin." / "owner."). 관리자 쿠키와 운영자 쿠키를 서로 바꿔 쓸 수 없게 한다
	 */
	private boolean verify(String purpose, String value) {
		String[] parts = value.split("\\.");
		if (parts.length != 2) {
			return false;
		}
		byte[] expected = sign(purpose + parts[0]).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, parts[1].getBytes(StandardCharsets.UTF_8))) {
			return false;
		}
		try {
			return clock.instant().getEpochSecond() < Long.parseLong(parts[0]);
		}
		catch (NumberFormatException ex) {
			return false;
		}
	}

	/** 관리자 쿠키 공통 속성: /admin 에서만 전송, 스크립트 접근 금지, 다른 사이트 요청에는 안 실림(Strict) */
	private ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
		return ResponseCookie.from(COOKIE, value).path("/admin").httpOnly(true).secure(secure).sameSite("Strict");
	}

	/** HMAC-SHA256 서명 (URL에 안전한 Base64) */
	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
