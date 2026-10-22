package io.github.minunglee.gateway.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.jayway.jsonpath.JsonPath;
import io.github.minunglee.gateway.TestcontainersConfiguration;
import io.github.minunglee.gateway.invite.InviteAccessLog;
import io.github.minunglee.gateway.invite.InviteService;
import io.github.minunglee.gateway.visit.JipdonCollectStatus;
import io.github.minunglee.gateway.visit.JipdonStatsClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
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

	@Autowired
	InviteService inviteService;

	@MockitoBean
	JipdonStatsClient jipdonStats;

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
			.andExpect(content().string(containsString("사용량")))
			.andExpect(content().string(containsString("전체 보기")))
			.andExpect(content().string(containsString("수집 서버에 연결되지 않음")));

		mockMvc.perform(put("/admin/killswitch").cookie(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\": false}")).andExpect(jsonPath("$.killSwitch").value(false));
	}

	@Test
	void 집돈_수집_현황을_보여준다() throws Exception {
		Instant last = Instant.parse("2026-10-16T02:00:00Z"); // 11:00 KST
		given(jipdonStats.fetchCollect()).willReturn(Optional.of(new JipdonCollectStatus(true, true, "0 0 */6 * * *", 8000,
				last, 49152, 12, 4915, List.of(new JipdonCollectStatus.Run(last.minusSeconds(125), last, 2048, 2100, 37, 1,
						true, 300)))));
		Cookie admin = mockMvc.perform(post("/admin/login").param("token", "test-admin-token"))
			.andReturn()
			.getResponse()
			.getCookie(AdminSession.COOKIE);

		mockMvc.perform(get("/admin").cookie(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("실거래가 수집")))
			.andExpect(content().string(containsString("10/16 11:00")))
			.andExpect(content().string(containsString("90% (남은 달 4915 / 전체 49152)")))
			.andExpect(content().string(containsString("2분 5초")))
			.andExpect(content().string(containsString("하루 한도 도달")));
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

	@Test
	void 사용량은_7일_30일_전체로_볼_수_있다() throws Exception {
		Cookie admin = adminCookie();
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

		mockMvc.perform(get("/admin").param("range", "30").cookie(admin))
			.andExpect(content().string(containsString(today.minusDays(29).toString())))
			.andExpect(content().string(not(containsString(today.minusDays(30).toString()))));
		mockMvc.perform(get("/admin").param("range", "all").cookie(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin").cookie(admin))
			.andExpect(content().string(not(containsString(today.minusDays(7).toString()))));
	}

	@Test
	void 회사별_상세에서_날짜별_요약과_전체_기록을_본다() throws Exception {
		Cookie admin = adminCookie();
		long id = createInvite(admin, "I사");
		inviteService.log(id, InviteAccessLog.Action.REDEEM);
		inviteService.log(id, InviteAccessLog.Action.RESUME_VIEW);

		mockMvc.perform(get("/admin/codes/" + id).cookie(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("I사")))
			.andExpect(content().string(containsString("날짜별 요약")))
			.andExpect(content().string(containsString("이력서 열람")));
		mockMvc.perform(get("/admin/codes/999999").cookie(admin)).andExpect(status().isNotFound());
	}

	@Test
	void 전체_기록은_회사와_행동으로_거르고_페이지를_넘긴다() throws Exception {
		Cookie admin = adminCookie();
		long id = createInvite(admin, "J사");
		long other = createInvite(admin, "K사");
		for (int i = 0; i < 55; i++) {
			inviteService.log(id, InviteAccessLog.Action.RESUME_VIEW);
		}
		inviteService.log(other, InviteAccessLog.Action.REDEEM);

		mockMvc.perform(get("/admin/access").param("code", String.valueOf(id)).cookie(admin))
			.andExpect(content().string(containsString("총 55건")))
			.andExpect(content().string(containsString("1 / 2")));
		mockMvc.perform(get("/admin/access").param("code", String.valueOf(id)).param("page", "2").cookie(admin))
			.andExpect(content().string(containsString("2 / 2")));
		mockMvc.perform(get("/admin/access").param("action", "REDEEM").cookie(admin))
			.andExpect(content().string(containsString("총 1건")));
	}

	@Test
	void 상세와_전체_기록도_로그인해야_볼_수_있다() throws Exception {
		mockMvc.perform(get("/admin/codes/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/access")).andExpect(status().isUnauthorized());
	}

	private Cookie adminCookie() throws Exception {
		return login("test-admin-token", "10.2.1.1").andReturn().getResponse().getCookie(AdminSession.COOKIE);
	}

	private long createInvite(Cookie admin, String label) throws Exception {
		String body = mockMvc
			.perform(post("/admin/invites").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\": \"" + label + "\", \"validDays\": 30, \"dailyLimit\": 100}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions login(String token, String remoteAddr) throws Exception {
		return mockMvc.perform(post("/admin/login").param("token", token).with(request -> {
			request.setRemoteAddr(remoteAddr);
			return request;
		}));
	}

}
