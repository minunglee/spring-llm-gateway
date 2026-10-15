package io.github.minunglee.gateway.chat;

import io.github.minunglee.gateway.TestcontainersConfiguration;
import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.Tier;
import io.github.minunglee.gateway.log.RequestLog;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms",
		"gateway.admin.token=test-admin-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoChatIntegrationTest {

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

	@Test
	void 질문을_보내면_응답과_사용량을_돌려주고_기록한다() throws Exception {
		chat("안녕하세요", "10.0.0.1").andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("mock"))
			.andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
			.andExpect(jsonPath("$.inputTokens").value(2))
			.andExpect(jsonPath("$.remainingThisHour").value(4))
			.andExpect(jsonPath("$.remainingToday").value(19))
			// 게이트웨이가 거친 단계: 봇 확인(로컬은 꺼짐) → 사용량 제한 → 예산 선차감 → AI 호출 → 정산
			.andExpect(jsonPath("$.trace[*].name").value(org.hamcrest.Matchers.contains("봇 확인", "사용량 제한",
					"예산 선차감", "AI 호출 (가짜 응답 모드)", "정산")))
			.andExpect(jsonPath("$.trace[0].status").value("SKIPPED"))
			.andExpect(jsonPath("$.trace[1].detail").value("이번 시간 4회 · 오늘 19회 남음"))
			.andExpect(jsonPath("$.trace[4].detail").value(containsString("반환")));

		assertThat(requestLogRepository.findAll()).singleElement().satisfies(log -> {
			assertThat(log.getStatus()).isEqualTo(RequestLog.Status.SUCCESS);
			assertThat(log.getCostMicroUsd()).isPositive();
		});
		// 선차감 후 실제 비용으로 정산되어, 예산 사용액 = 기록된 비용
		assertThat(budgetGuard.usedTodayMicroUsd(Tier.GUEST))
			.isEqualTo(requestLogRepository.findAll().getFirst().getCostMicroUsd());
	}

	@Test
	void 시간당_한도를_넘으면_429와_재시도_시간을_돌려준다() throws Exception {
		for (int i = 0; i < 5; i++) {
			chat("질문", "10.0.0.2").andExpect(status().isOk());
		}

		chat("질문", "10.0.0.2").andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "720"))
			.andExpect(jsonPath("$.detail").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
			// 막힌 경우에도 어느 단계에서 막혔는지 알려준다
			.andExpect(jsonPath("$.trace[-1].name").value("사용량 제한"))
			.andExpect(jsonPath("$.trace[-1].status").value("BLOCKED"));

		// 다른 IP는 영향 없음
		chat("질문", "10.0.0.3").andExpect(status().isOk());
	}

	@Test
	void 예산이_소진되면_LLM을_호출하지_않고_데모_응답을_준다() throws Exception {
		budgetGuard.reserve(Tier.GUEST, budgetGuard.limitMicroUsd(Tier.GUEST)).orElseThrow();

		chat("질문", "10.0.0.4").andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("demo"))
			.andExpect(jsonPath("$.answer").value(containsString("예산이 모두 소진")))
			.andExpect(jsonPath("$.trace[-1].name").value("예산 선차감"))
			.andExpect(jsonPath("$.trace[-1].status").value("BLOCKED"));

		assertThat(requestLogRepository.findAll()).singleElement()
			.satisfies(log -> assertThat(log.getStatus()).isEqualTo(RequestLog.Status.DEGRADED));
	}

	@Test
	void 긴급_차단을_켜면_데모_응답만_준다() throws Exception {
		mockMvc.perform(put("/admin/killswitch").header("Authorization", "Bearer test-admin-token")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\": true}")).andExpect(status().isOk()).andExpect(jsonPath("$.killSwitch").value(true));

		chat("질문", "10.0.0.5").andExpect(jsonPath("$.mode").value("demo"))
			.andExpect(jsonPath("$.answer").value(containsString("일시 중단")));
	}

	@Test
	void 관리자_API는_토큰이_없으면_막는다() throws Exception {
		mockMvc.perform(get("/admin/status")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/status").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/status").header("Authorization", "Bearer test-admin-token"))
			.andExpect(status().isOk());
	}

	@Test
	void 오늘_예산_사용_현황을_보여준다() throws Exception {
		chat("질문", "10.0.0.6");

		mockMvc.perform(get("/demo/budget")).andExpect(status().isOk()).andExpect(jsonPath("$.limit").value("$0.5"));
	}

	@Test
	void 빈_질문은_거부하고_횟수를_차감하지_않는다() throws Exception {
		chat("   ", "10.0.0.7").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("질문을 입력해주세요"));

		assertThat(requestLogRepository.count()).isZero();
		chat("질문", "10.0.0.7").andExpect(jsonPath("$.remainingThisHour").value(4));
	}

	@Test
	void 너무_긴_질문은_거부한다() throws Exception {
		chat("가".repeat(2001), "10.0.0.8").andExpect(status().isBadRequest());
	}

	@Test
	void 체험_페이지에_현재_모드를_표시한다() throws Exception {
		mockMvc.perform(get("/gateway"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("가짜 응답 모드")))
			.andExpect(content().string(containsString("요청 하나가 지나가는 길")))
			.andExpect(content().string(containsString("게이트웨이가 방금 한 일")));
	}

	@Test
	void 메인_페이지에_프로필과_포트폴리오를_보여준다() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이력서 및 경력기술서")))
			.andExpect(content().string(containsString("AI 비용 방어 게이트웨이")));
	}

	@Test
	void 이력서는_초대코드_없이는_잠금_안내를_보여준다() throws Exception {
		mockMvc.perform(get("/resume"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("초대코드가 있는 분만")));
	}

	private ResultActions chat(String message, String remoteAddr) throws Exception {
		return mockMvc.perform(post("/demo/chat").contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\": \"" + message + "\"}")
			.with(request -> {
				request.setRemoteAddr(remoteAddr);
				return request;
			}));
	}

}
