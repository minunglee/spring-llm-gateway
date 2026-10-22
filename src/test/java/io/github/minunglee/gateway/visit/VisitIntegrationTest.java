package io.github.minunglee.gateway.visit;

import io.github.minunglee.gateway.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.admin.token=test-admin-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VisitIntegrationTest {

	static final String CHROME = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE page_view");
		redis.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	int count() {
		return jdbc.queryForObject("SELECT count(*) FROM page_view", Integer.class);
	}

	@Test
	void 화면_페이지만_기록하고_IP는_남기지_않는다() throws Exception {
		mockMvc.perform(get("/").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9").header("Referer", "https://www.saramin.co.kr/zf_user/x")
			.with(r -> {
				r.setRemoteAddr("203.0.113.7");
				return r;
			})).andExpect(status().isOk());
		mockMvc.perform(get("/jipdon").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9")).andExpect(status().isOk());
		mockMvc.perform(get("/demo/budget").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9")); // API는 세지 않는다

		assertThat(count()).isEqualTo(2);
		var row = jdbc.queryForMap("SELECT path, referrer_host, visitor FROM page_view WHERE path = '/'");
		assertThat(row.get("referrer_host")).isEqualTo("www.saramin.co.kr");
		assertThat((String) row.get("visitor")).hasSize(16).doesNotContain("203.0.113.7");
	}

	@Test
	void 로봇과_운영자_본인은_세지_않는다() throws Exception {
		mockMvc.perform(get("/").header("User-Agent", "Googlebot/2.1")).andExpect(status().isOk());
		assertThat(count()).isZero();

		// 관리자로 로그인하면 사이트 전체에 실리는 운영자 표시 쿠키가 생기고, 그 브라우저의 방문은 세지 않는다
		var login = mockMvc.perform(post("/admin/login").param("token", "test-admin-token")).andReturn().getResponse();
		jakarta.servlet.http.Cookie owner = login.getCookie("owner");
		assertThat(owner).isNotNull();
		assertThat(owner.getPath()).isEqualTo("/");
		mockMvc.perform(get("/").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9").cookie(owner)).andExpect(status().isOk());
		assertThat(count()).isZero();

		// 위조한 값은 통하지 않는다
		mockMvc.perform(get("/").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9").cookie(new jakarta.servlet.http.Cookie("owner", "9999999999.fake")));
		assertThat(count()).isEqualTo(1);
	}

	@Test
	void 새로고침은_30분_안에_한_번만_센다() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(get("/").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9")).andExpect(status().isOk());
		}
		assertThat(count()).isEqualTo(1);
		mockMvc.perform(get("/jipdon").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9")); // 다른 페이지는 따로 센다
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void 관리자_방문_통계_화면() throws Exception {
		mockMvc.perform(get("/").header("User-Agent", CHROME).header("Accept-Language", "ko-KR,ko;q=0.9"));
		mockMvc.perform(get("/admin/visits").header("Authorization", "Bearer test-admin-token"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("포트폴리오 · minunglee.cloud")))
			.andExpect(content().string(containsString("오늘 방문자 · 조회 1")))
			// 테스트에는 jipdon-api가 없어 연결되지 않음으로 보인다
			.andExpect(content().string(containsString("통계 서버에 연결되지 않았습니다")));
		// 사이트 블록은 두 개만 (조각 정의가 화면에 한 번 더 그려지지 않게)
		String html = mockMvc.perform(get("/admin/visits").header("Authorization", "Bearer test-admin-token"))
			.andReturn().getResponse().getContentAsString();
		assertThat(html.split("통계 서버에 연결되지 않았습니다", -1)).hasSize(2);
		mockMvc.perform(get("/admin/visits")).andExpect(status().isUnauthorized());
	}

}
