package io.github.minunglee.gateway.visit;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * jipdon.com 방문 통계를 같은 서버의 jipdon-api에서 읽는다.
 * 주소(jipdon-api:8090/internal)는 Docker 네트워크 안에서만 닿고, 공유 토큰을 함께 보낸다.
 * 토큰이 없거나(로컬 개발) 응답이 없으면 관리자 화면에 "연결되지 않음"으로 보인다.
 */
@Component
public class JipdonStatsClient {

	private static final Logger log = LoggerFactory.getLogger(JipdonStatsClient.class);

	private final RestClient restClient;

	private final String url;

	private final String token;

	JipdonStatsClient(@Value("${gateway.visit.jipdon-stats-url:http://jipdon-api:8090/internal/stats}") String url,
			@Value("${gateway.visit.jipdon-token:}") String token) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.url = url;
		this.token = token;
	}

	public Optional<VisitStats> fetch(int days) {
		if (token.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(restClient.get()
				.uri(url + "?days=" + days)
				.header("X-Internal-Token", token)
				.retrieve()
				.body(VisitStats.class));
		}
		catch (RestClientException ex) {
			log.warn("jipdon 방문 통계를 읽지 못했습니다: {}", ex.getMessage());
			return Optional.empty();
		}
	}

}
