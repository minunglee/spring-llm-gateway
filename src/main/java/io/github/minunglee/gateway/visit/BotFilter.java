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

	/** 이름에 드러나는 로봇·도구 (검색엔진, 미리보기, curl·python 같은 스크립트, 보안 스캐너·연구용 수집기) */
	private static final Pattern BOT_NAME = Pattern.compile(
			"bot|crawl|spider|slurp|preview|headless|lighthouse|curl|wget|python|java/|k6|scan|research|inspect",
			Pattern.CASE_INSENSITIVE);

	/** User-Agent에서 크롬 주 버전 숫자를 뽑는다 */
	private static final Pattern CHROME = Pattern.compile("Chrome/(\\d+)");

	/** User-Agent에서 iOS 주 버전 숫자를 뽑는다 */
	private static final Pattern IOS = Pattern.compile("(?:iPhone|iPad|CPU) OS (\\d+)_");

	/** 이보다 오래된 크롬은 스캐너로 본다. 크롬은 자동 업데이트되어 실제 사람은 거의 최신 버전을 쓴다 (로그에서 78~111을 흉내 낸 스캐너 확인) */
	static final int MIN_CHROME = 120;

	/** 이보다 오래된 iOS는 스캐너로 본다 (로그 최다 스캐너가 2019년 iOS 13을 흉내 냄) */
	static final int MIN_IOS = 15;

	private BotFilter() {
	}

	/**
	 * 로봇·스캐너로 보이면 true. 이름에 로봇 표시, 언어 헤더 없음(브라우저는 항상 보냄), 빈 Referer, 오래된 브라우저 버전 중 하나라도 해당
	 *
	 * @param referer null이면 검사하지 않음 (집돈 비컨처럼 빈 값이 정상인 경우)
	 */
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

	/** 버전 숫자가 있고 기준보다 낮은지 (버전 표시가 없으면 판단하지 않음) */
	private static boolean olderThan(Matcher version, int minimum) {
		return version.find() && Integer.parseInt(version.group(1)) < minimum;
	}

}
