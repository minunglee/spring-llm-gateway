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
class AdminSession {

	static final String COOKIE = "admin";

	private static final Duration MAX_AGE = Duration.ofHours(12);

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

	boolean enabled() {
		return token != null;
	}

	boolean matchesToken(String candidate) {
		return token != null && candidate != null
				&& MessageDigest.isEqual(token, candidate.strip().getBytes(StandardCharsets.UTF_8));
	}

	ResponseCookie issue(boolean secure) {
		long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
		String payload = "admin." + expiresAt;
		return base(expiresAt + "." + sign(payload), secure).maxAge(MAX_AGE).build();
	}

	ResponseCookie clear(boolean secure) {
		return base("", secure).maxAge(0).build();
	}

	boolean isLoggedIn(HttpServletRequest request) {
		if (!enabled() || request.getCookies() == null) {
			return false;
		}
		return Arrays.stream(request.getCookies())
			.filter(cookie -> COOKIE.equals(cookie.getName()))
			.map(Cookie::getValue)
			.anyMatch(this::verify);
	}

	private boolean verify(String value) {
		String[] parts = value.split("\\.");
		if (parts.length != 2) {
			return false;
		}
		byte[] expected = sign("admin." + parts[0]).getBytes(StandardCharsets.UTF_8);
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

	private ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
		return ResponseCookie.from(COOKIE, value).path("/admin").httpOnly(true).secure(secure).sameSite("Strict");
	}

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
