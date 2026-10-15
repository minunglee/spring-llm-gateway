package io.github.minunglee.gateway.bot;

import com.jayway.jsonpath.JsonPath;
import io.github.minunglee.gateway.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 봇 확인이 켜진 상태. Cloudflare 호출은 가짜(Mock)로 바꿔 네트워크 없이 검증한다.
 */
@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms",
		"gateway.admin.token=test-admin-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TurnstileIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	StringRedisTemplate redis;

	@MockitoBean
	TurnstileVerifier turnstileVerifier;

	@BeforeEach
	void setUp() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
		given(turnstileVerifier.enabled()).willReturn(true);
		given(turnstileVerifier.siteKey()).willReturn("test-site-key");
		given(turnstileVerifier.verify(any(), any())).willReturn(false);
		given(turnstileVerifier.verify(eq("valid-token"), any())).willReturn(true);
	}

	@Test
	void 게스트가_봇_확인_토큰_없이_보내면_거부한다() throws Exception {
		chat(null, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(containsString("자동화된 요청")))
			.andExpect(jsonPath("$.trace[0].name").value("봇 확인"))
			.andExpect(jsonPath("$.trace[0].status").value("BLOCKED"));
	}

	@Test
	void 확인된_토큰이면_통과한다() throws Exception {
		chat("valid-token", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("mock"))
			.andExpect(jsonPath("$.trace[0].status").value("OK"));
	}

	@Test
	void 초대코드_사용자는_봇_확인을_하지_않는다() throws Exception {
		Cookie invite = redeemInvite();

		chat(null, invite).andExpect(status().isOk());
	}

	@Test
	void 게스트에게만_봇_확인_위젯을_보여준다() throws Exception {
		mockMvc.perform(get("/gateway")).andExpect(content().string(containsString("data-sitekey=\"test-site-key\"")));
		mockMvc.perform(get("/gateway").cookie(redeemInvite()))
			.andExpect(content().string(not(containsString("data-sitekey"))));
	}

	private ResultActions chat(String token, Cookie invite) throws Exception {
		String body = token == null ? "{\"message\": \"질문\"}"
				: "{\"message\": \"질문\", \"turnstileToken\": \"" + token + "\"}";
		var request = post("/demo/chat").contentType(MediaType.APPLICATION_JSON).content(body);
		if (invite != null) {
			request.cookie(invite);
		}
		return mockMvc.perform(request);
	}

	private Cookie redeemInvite() throws Exception {
		String created = mockMvc
			.perform(post("/admin/invites").header("Authorization", "Bearer test-admin-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\": \"T사\", \"validDays\": 30, \"dailyLimit\": 100}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String code = JsonPath.read(created, "$.code");
		return mockMvc.perform(post("/invite").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\": \"" + code + "\"}")).andReturn().getResponse().getCookie("invite");
	}

}
