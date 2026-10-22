package io.github.minunglee.gateway.visit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 방문 통계에서 사람이 아닌 요청을 거른다.
 *
 * 운영 로그를 보니 대부분의 스캐너가 브라우저처럼 보이는 User-Agent를 쓴다(iOS 13, Chrome 78·111 등).
 * 이름에 bot이 들어간 것만으로는 걸러지지 않아, 실제 브라우저라면 반드시 갖는 특징으로 판단한다.
 * - 이름에 bot·crawler·headless 같은 표시가 있음
 * - Accept-Language가 없음 (사람이 쓰는 브라우저는 항상 보낸다)
 * - Referer 헤더는 있는데 비어 있음 (브라우저는 이렇게 보내지 않는다)
 * - 너무 오래된 브라우저: Chrome 120 미만(2023년 말), iOS 15 미만. 실제 사용자 브라우저는 자동으로 업데이트된다
 */
final class BotFilter {

	private static final Pattern BOT_NAME = Pattern.compile(
			"bot|crawl|spider|slurp|preview|headless|lighthouse|curl|wget|python|java/|k6|scan|research|inspect",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern CHROME = Pattern.compile("Chrome/(\\d+)");

	private static final Pattern IOS = Pattern.compile("(?:iPhone|iPad|CPU) OS (\\d+)_");

	static final int MIN_CHROME = 120;

	static final int MIN_IOS = 15;

	private BotFilter() {
	}

	static boolean isBot(String userAgent, String acceptLanguage, String referer) {
		if (userAgent == null || userAgent.isBlank() || BOT_NAME.matcher(userAgent).find()) {
			return true;
		}
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return true;
		}
		if (referer != null && referer.isBlank()) {
			return true;
		}
		return olderThan(CHROME.matcher(userAgent), MIN_CHROME) || olderThan(IOS.matcher(userAgent), MIN_IOS);
	}

	private static boolean olderThan(Matcher version, int minimum) {
		return version.find() && Integer.parseInt(version.group(1)) < minimum;
	}

}
