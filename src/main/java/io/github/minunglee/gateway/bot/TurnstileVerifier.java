package io.github.minunglee.gateway.bot;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloudflare Turnstile 봇 확인.
 * <p>
 * 브라우저가 받은 일회용 토큰을 서버에서 Cloudflare에 다시 확인한다. 토큰은 한 번만 쓸 수 있어서
 * 스크립트가 토큰 하나로 요청을 반복할 수 없다. 비밀 키가 없으면(로컬 개발) 확인을 건너뛴다.
 * <p>
 * Cloudflare에 연결할 수 없으면 통과시키지 않는다(fail-closed). 비용이 나가는 호출 앞이라
 * 확인하지 못한 요청을 들여보내지 않는 쪽을 택한다.
 */
@Component
@EnableConfigurationProperties(TurnstileVerifier.Properties.class)
public class TurnstileVerifier {

	private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

	/** Cloudflare Turnstile 토큰 검증 주소 (화면 위젯이 준 토큰이 진짜 사람에게서 나왔는지 서버가 다시 확인) */
	private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private final Properties properties;

	private final RestClient restClient;

	TurnstileVerifier(Properties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** 사이트 키와 비밀 키가 모두 설정됐을 때만 봇 확인을 한다 (로컬 개발은 꺼짐) */
	public boolean enabled() {
		return StringUtils.hasText(properties.secretKey()) && StringUtils.hasText(properties.siteKey());
	}

	/** 화면에 위젯을 그릴 때 쓰는 공개 키 */
	public String siteKey() {
		return properties.siteKey();
	}

	/** 토큰을 Cloudflare에 보내 확인한다. 꺼져 있으면 통과, 토큰이 없거나 확인 요청이 실패하면 거부(fail-closed) */
	public boolean verify(String token, String remoteIp) {
		if (!enabled()) {
			return true;
		}
		if (!StringUtils.hasText(token)) {
			return false;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", properties.secretKey());
		form.add("response", token);
		form.add("remoteip", remoteIp);
		try {
			VerifyResponse response = restClient.post()
				.uri(VERIFY_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(VerifyResponse.class);
			return response != null && response.success();
		}
		catch (RestClientException ex) {
			log.warn("Turnstile 확인 요청 실패, 요청을 거부합니다: {}", ex.getMessage());
			return false;
		}
	}

	/** Cloudflare 응답 중 쓰는 값 (사람으로 확인됐는지) */
	record VerifyResponse(boolean success) {
	}

	/**
	 * @param siteKey 공개 키 (화면용)
	 * @param secretKey 비밀 키 (서버 확인용, 서버 .env에만 둔다)
	 */
	@ConfigurationProperties("gateway.turnstile")
	record Properties(String siteKey, String secretKey) {
	}

}
