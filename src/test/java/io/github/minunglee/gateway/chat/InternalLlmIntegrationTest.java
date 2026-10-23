package io.github.minunglee.gateway.chat;

import io.github.minunglee.gateway.TestcontainersConfiguration;
import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.Tier;
import io.github.minunglee.gateway.log.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms",
		"gateway.internal.jipdon-token=test-internal-token", "gateway.limit.jipdon.hourly=3" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InternalLlmIntegrationTest {

	static final String TOKEN = "test-internal-token";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RequestLogRepository requestLogRepository;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	BudgetGuard budgetGuard;

	@BeforeEach
	void setUp() {
		requestLogRepository.deleteAll();
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	ResultActions call(String token, String subject, String message) throws Exception {
		var request = post("/internal/llm").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"subject": "%s", "system": "집돈 계산기 안내", "message": "%s", "maxTokens": 300}
					""".formatted(subject, message));
		return mockMvc.perform(token == null ? request : request.header("X-Internal-Token", token));
	}

	@Test
	void 토큰이_없거나_틀리면_주소가_없는_것처럼_404() throws Exception {
		call(null, "visitor-0001", "전세 대출").andExpect(status().isNotFound());
		call("wrong", "visitor-0001", "전세 대출").andExpect(status().isNotFound());
		// 입력이 잘못돼도 토큰이 없으면 400이 아니라 404 (주소가 있다는 걸 알려주지 않는다)
		call(null, "x", "").andExpect(status().isNotFound());
		assertThat(requestLogRepository.count()).isZero();
	}

	@Test
	void 서비스_몫의_등급과_예산으로_호출하고_기록한다() throws Exception {
		call(TOKEN, "visitor-0001", "전세 4억이면 대출 얼마나").andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("mock"))
			.andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
			.andExpect(jsonPath("$.degraded").value(false))
			.andExpect(jsonPath("$.remainingToday").value(29));

		assertThat(requestLogRepository.findAll()).singleElement()
			.satisfies(log -> assertThat(log.getTier()).isEqualTo(Tier.JIPDON));
		// 포트폴리오 체험(GUEST) 예산은 건드리지 않는다
		assertThat(budgetGuard.usedTodayMicroUsd(Tier.JIPDON)).isPositive();
		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST)).isZero();
	}

	@Test
	void 최종_사용자마다_따로_한도를_센다() throws Exception {
		for (int i = 0; i < 3; i++) {
			call(TOKEN, "visitor-0001", "질문").andExpect(status().isOk());
		}
		call(TOKEN, "visitor-0001", "질문").andExpect(status().isTooManyRequests());
		call(TOKEN, "visitor-0002", "질문").andExpect(status().isOk());
	}

	@Test
	void 잘못된_입력은_토큰이_맞을_때만_400() throws Exception {
		call(TOKEN, "short", "질문").andExpect(status().isBadRequest());
		call(TOKEN, "visitor-0001", "").andExpect(status().isBadRequest());
	}

}
