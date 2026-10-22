package io.github.minunglee.gateway.visit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 로그에 실제로 찍힌 User-Agent로 사람·스캐너 구분을 확인한다 */
class BotFilterTest {

	static final String KO = "ko-KR,ko;q=0.9";

	@Test
	void 최신_브라우저는_사람으로_본다() {
		assertThat(BotFilter.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36", KO, null)).isFalse();
		assertThat(BotFilter.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1", KO, "https://www.saramin.co.kr/")).isFalse();
		assertThat(BotFilter.isBot("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/30.0 Chrome/143.0.0.0 Mobile Safari/537.36", KO, null)).isFalse();
	}

	@Test
	void 브라우저인_척하는_오래된_버전은_스캐너로_본다() {
		// 로그에서 가장 많았던 iOS 13 (2019년) 175회, Chrome 111·78
		assertThat(BotFilter.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1", KO, null)).isTrue();
		assertThat(BotFilter.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36", KO, null)).isTrue();
		assertThat(BotFilter.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36", KO, null)).isTrue();
	}

	@Test
	void 이름을_밝힌_스캐너와_도구() {
		assertThat(BotFilter.isBot("Mozilla/5.0 (compatible; CensysInspect/1.1; +https://about.censys.io/)", KO, null)).isTrue();
		assertThat(BotFilter.isBot("Software Security Research/1.0 (+https://finn.softsec.ruhr-uni-bochum.de)", KO, null)).isTrue();
		assertThat(BotFilter.isBot("python-requests/2.31.0", KO, null)).isTrue();
		assertThat(BotFilter.isBot("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.0.0 Safari/537.36", KO, null)).isTrue();
	}

	@Test
	void 사람_브라우저라면_있어야_할_헤더가_없으면_스캐너() {
		String chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
		assertThat(BotFilter.isBot(chrome, null, null)).isTrue(); // Accept-Language 없음
		assertThat(BotFilter.isBot(chrome, KO, "")).isTrue(); // 빈 Referer
	}

}
