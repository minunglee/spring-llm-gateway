package io.github.minunglee.gateway.invite;

import java.util.regex.Pattern;

/**
 * 링크 미리보기·수집 봇 판별. 메신저(카카오톡·슬랙 등)에 초대 링크를 붙여 넣으면 미리보기를 만들려고 링크를 먼저 열어서,
 * 사람이 연 것처럼 "코드 입력"이 찍혔다 (운영 로그: facebookexternalhit/1.1; kakaotalk-scrap/1.0).
 * <p>
 * 방문 통계용 필터(BotFilter)보다 느슨하게 이름만 본다. 여기서 사람을 봇으로 잘못 보면 회사가 이력서를 못 열기 때문이다.
 * 카카오톡 인앱 브라우저(KAKAOTALK)는 사람이므로 "kakaotalk"만으로는 거르지 않는다.
 */
final class LinkPreviewBots {

	private static final Pattern PREVIEW = Pattern.compile(
			"bot|crawl|spider|slurp|preview|scrap|facebookexternalhit|embedly|headless|curl|wget|python|go-http|okhttp|java/|axios|node-fetch",
			Pattern.CASE_INSENSITIVE);

	private LinkPreviewBots() {
	}

	/** User-Agent 이름에 미리보기·수집 봇이나 스크립트 표시가 있으면 true (User-Agent가 없으면 판단하지 않음) */
	static boolean matches(String userAgent) {
		return userAgent != null && PREVIEW.matcher(userAgent).find();
	}

}
