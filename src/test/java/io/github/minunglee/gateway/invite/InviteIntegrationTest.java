package io.github.minunglee.gateway.invite;

import com.jayway.jsonpath.JsonPath;
import io.github.minunglee.gateway.TestcontainersConfiguration;
import io.github.minunglee.gateway.limit.BudgetGuard;
import io.github.minunglee.gateway.limit.Tier;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms",
		"gateway.admin.token=test-admin-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InviteIntegrationTest {

	private static final String ADMIN = "Bearer test-admin-token";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	BudgetGuard budgetGuard;

	@Autowired
	InviteAccessLogRepository accessLogRepository;

	@BeforeEach
	void setUp() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	void 초대코드를_입력하면_Sonnet_모델과_코드별_한도가_적용된다() throws Exception {
		Cookie invite = redeem(createInvite("A사", 50), "10.1.0.1");

		chat(invite, "10.1.0.1").andExpect(status().isOk())
			.andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
			.andExpect(jsonPath("$.remainingToday").value(49));
		// 게스트는 Haiku
		chat(null, "10.1.0.2").andExpect(jsonPath("$.model").value("claude-haiku-5-5"));
	}

	@Test
	void 코드는_대소문자와_하이픈_없이_입력해도_인식한다() throws Exception {
		String code = createInvite("B사", 100);

		postRedeem(code.replace("-", "").toLowerCase(), "10.1.0.3").andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("B사"));
	}

	@Test
	void 잘못된_코드는_거부한다() throws Exception {
		postRedeem("AAAA-BBBB-CCCC", "10.1.0.4").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("유효하지 않거나 만료된 초대코드입니다."))
			.andExpect(cookie().doesNotExist("invite"));
	}

	@Test
	void 코드_입력_시도는_시간당_10회로_제한한다() throws Exception {
		for (int i = 0; i < 10; i++) {
			postRedeem("WRONG-CODE-" + i, "10.1.0.5").andExpect(status().isBadRequest());
		}
		postRedeem("WRONG-CODE-X", "10.1.0.5").andExpect(status().isTooManyRequests());
	}

	@Test
	void 서명이_위조된_쿠키는_게스트로_취급한다() throws Exception {
		Cookie invite = redeem(createInvite("C사", 100), "10.1.0.6");
		String[] parts = invite.getValue().split("\\.");
		Cookie forged = new Cookie("invite", "999." + parts[1] + "." + parts[2]);

		chat(forged, "10.1.0.6").andExpect(jsonPath("$.model").value("claude-haiku-5-5"));
	}

	@Test
	void 관리자가_폐기하면_바로_게스트로_돌아간다() throws Exception {
		String code = createInvite("D사", 100);
		Cookie invite = redeem(code, "10.1.0.7");
		long id = inviteId();

		mockMvc.perform(delete("/admin/invites/" + id).header("Authorization", ADMIN))
			.andExpect(status().isNoContent());

		chat(invite, "10.1.0.7").andExpect(jsonPath("$.model").value("claude-haiku-5-5"));
	}

	@Test
	void 게스트_예산이_소진돼도_초대코드_사용자는_쓸_수_있다() throws Exception {
		Cookie invite = redeem(createInvite("E사", 100), "10.1.0.8");
		budgetGuard.reserve(Tier.GUEST, budgetGuard.limitMicroUsd(Tier.GUEST)).orElseThrow();

		chat(null, "10.1.0.9").andExpect(jsonPath("$.mode").value("demo"));
		chat(invite, "10.1.0.8").andExpect(jsonPath("$.mode").value("mock"));
	}

	@Test
	void 이력서는_초대코드가_있으면_열리고_열람이_기록된다() throws Exception {
		mockMvc.perform(get("/resume")).andExpect(content().string(containsString("초대코드가 있는 분만")));

		Cookie invite = redeem(createInvite("F사", 100), "10.1.0.10");
		mockMvc.perform(get("/resume").cookie(invite))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("F사 초대코드로 열람 중")));

		mockMvc.perform(get("/admin/invites").header("Authorization", ADMIN))
			.andExpect(jsonPath("$[0].label").value("F사"))
			.andExpect(jsonPath("$[0].redeemCount").value(1))
			.andExpect(jsonPath("$[0].resumeViews").value(1));
	}

	@Test
	void 메인과_체험_페이지에_등급을_표시한다() throws Exception {
		Cookie invite = redeem(createInvite("G사", 100), "10.1.0.11");

		mockMvc.perform(get("/").cookie(invite)).andExpect(content().string(containsString("초대코드 사용 중")));
		mockMvc.perform(get("/gateway").cookie(invite)).andExpect(content().string(containsString("초대코드 · G사")));
		mockMvc.perform(get("/gateway")).andExpect(content().string(containsString("게스트")));
	}

	@Test
	void 초대코드_해제() throws Exception {
		mockMvc.perform(delete("/invite")).andExpect(status().isNoContent()).andExpect(cookie().maxAge("invite", 0));
	}

	private String createInvite(String label, int dailyLimit) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/admin/invites").header("Authorization", ADMIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\": \"" + label + "\", \"validDays\": 30, \"dailyLimit\": " + dailyLimit + "}"))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
	}

	private long inviteId() throws Exception {
		MvcResult result = mockMvc.perform(get("/admin/invites").header("Authorization", ADMIN)).andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$[0].id")).longValue();
	}

	private Cookie redeem(String code, String remoteAddr) throws Exception {
		return postRedeem(code, remoteAddr).andExpect(status().isOk()).andReturn().getResponse().getCookie("invite");
	}

	private ResultActions postRedeem(String code, String remoteAddr) throws Exception {
		return mockMvc.perform(post("/invite").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\": \"" + code + "\"}")
			.with(request -> {
				request.setRemoteAddr(remoteAddr);
				return request;
			}));
	}

	private ResultActions chat(Cookie invite, String remoteAddr) throws Exception {
		var request = post("/demo/chat").contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\": \"질문\"}")
			.with(r -> {
				r.setRemoteAddr(remoteAddr);
				return r;
			});
		if (invite != null) {
			request.cookie(invite);
		}
		return mockMvc.perform(request);
	}

}
