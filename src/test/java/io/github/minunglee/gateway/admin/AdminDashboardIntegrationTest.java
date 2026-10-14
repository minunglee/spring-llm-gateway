package io.github.minunglee.gateway.admin;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms",
		"gateway.admin.token=test-admin-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminDashboardIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	StringRedisTemplate redis;

	@BeforeEach
	void setUp() {
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	@Test
	void 로그인하지_않으면_로그인_화면만_보인다() throws Exception {
		mockMvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("관리자 로그인")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("초대코드 현황"))));
	}

	@Test
	void 토큰이_틀리면_로그인되지_않는다() throws Exception {
		login("wrong-token", "10.2.0.1").andExpect(redirectedUrl("/admin?error=invalid"))
			.andExpect(cookie().doesNotExist(AdminSession.COOKIE));
	}

	@Test
	void 로그인하면_대시보드와_관리_API를_쓸_수_있다() throws Exception {
		Cookie admin = login("test-admin-token", "10.2.0.2").andExpect(redirectedUrl("/admin"))
			.andExpect(cookie().httpOnly(AdminSession.COOKIE, true))
			.andExpect(cookie().path(AdminSession.COOKIE, "/admin"))
			.andReturn()
			.getResponse()
			.getCookie(AdminSession.COOKIE);
		assertThat(admin).isNotNull();

		mockMvc.perform(post("/admin/invites").cookie(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"label\": \"H사\", \"validDays\": 30, \"dailyLimit\": 100}")).andExpect(status().isCreated());

		mockMvc.perform(get("/admin").cookie(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("초대코드 현황")))
			.andExpect(content().string(containsString("H사")))
			.andExpect(content().string(containsString("최근 7일 사용량")));

		mockMvc.perform(put("/admin/killswitch").cookie(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\": false}")).andExpect(jsonPath("$.killSwitch").value(false));
	}

	@Test
	void 쿠키나_토큰_없이는_관리_API를_쓸_수_없다() throws Exception {
		mockMvc.perform(get("/admin/status")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/invites")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/status").cookie(new Cookie(AdminSession.COOKIE, "9999999999.forged")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 로그인_시도는_시간당_5회로_제한한다() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("wrong", "10.2.0.3").andExpect(redirectedUrl("/admin?error=invalid"));
		}
		// 맞는 토큰이어도 막힌다
		login("test-admin-token", "10.2.0.3").andExpect(redirectedUrl("/admin?error=locked"));
	}

	@Test
	void 로그아웃하면_쿠키를_지운다() throws Exception {
		mockMvc.perform(post("/admin/logout")).andExpect(cookie().maxAge(AdminSession.COOKIE, 0));
	}

	private ResultActions login(String token, String remoteAddr) throws Exception {
		return mockMvc.perform(post("/admin/login").param("token", token).with(request -> {
			request.setRemoteAddr(remoteAddr);
			return request;
		}));
	}

}
