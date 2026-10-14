package io.github.minunglee.gateway.invite;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 초대코드를 한 번 입력하면 기억하기 위한 서명 쿠키.
 * <p>
 * 값은 {@code 코드ID.만료시각.서명} 형태다. 서명은 서버만 아는 비밀값으로 만든 HMAC이라
 * 사용자가 코드 ID나 만료시각을 바꾸면 서명이 맞지 않아 무시된다. 세션 저장소가 필요 없다.
 */
@Component
class InviteCookie {

	static final String NAME = "invite";

	private final SecretKeySpec key;

	private final Duration maxAge;

	private final Clock clock;

	InviteCookie(InviteProperties properties, Clock clock) {
		this.key = new SecretKeySpec(properties.cookieSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.maxAge = properties.cookieMaxAge();
		this.clock = clock;
	}

	ResponseCookie issue(InviteCode code, boolean secure) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(maxAge).isBefore(code.getExpiresAt()) ? now.plus(maxAge) : code.getExpiresAt();
		String payload = code.getId() + "." + expiresAt.getEpochSecond();
		return base(payload + "." + sign(payload), secure).maxAge(Duration.between(now, expiresAt)).build();
	}

	ResponseCookie clear(boolean secure) {
		return base("", secure).maxAge(0).build();
	}

	/** 서명이 맞고 만료되지 않은 쿠키의 초대코드 ID */
	Optional<Long> read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> NAME.equals(cookie.getName()))
			.findFirst()
			.flatMap(cookie -> verify(cookie.getValue()));
	}

	private Optional<Long> verify(String value) {
		String[] parts = value.split("\\.");
		if (parts.length != 3) {
			return Optional.empty();
		}
		String payload = parts[0] + "." + parts[1];
		byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) {
			return Optional.empty();
		}
		try {
			if (clock.instant().getEpochSecond() >= Long.parseLong(parts[1])) {
				return Optional.empty();
			}
			return Optional.of(Long.parseLong(parts[0]));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	private ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
		// HttpOnly: 스크립트에서 읽을 수 없게 해 XSS로 탈취되는 것을 막는다
		return ResponseCookie.from(NAME, value).path("/").httpOnly(true).secure(secure).sameSite("Lax");
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
