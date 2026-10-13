package io.github.minunglee.gateway.chat;

import io.github.minunglee.gateway.TestcontainersConfiguration;
import io.github.minunglee.gateway.log.RequestLog;
import io.github.minunglee.gateway.log.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "gateway.llm.mode=mock", "gateway.llm.mock-latency=0ms" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoChatIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RequestLogRepository requestLogRepository;

	@BeforeEach
	void setUp() {
		requestLogRepository.deleteAll();
	}

	@Test
	void 질문을_보내면_응답과_사용량을_돌려주고_기록한다() throws Exception {
		mockMvc.perform(post("/demo/chat").contentType(MediaType.APPLICATION_JSON).content("""
				{"message": "안녕하세요"}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("mock"))
			.andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
			.andExpect(jsonPath("$.inputTokens").value(2))
			.andExpect(jsonPath("$.cost").exists());

		assertThat(requestLogRepository.findAll()).singleElement().satisfies(log -> {
			assertThat(log.getStatus()).isEqualTo(RequestLog.Status.SUCCESS);
			assertThat(log.getInputTokens()).isEqualTo(2);
			assertThat(log.getCostMicroUsd()).isPositive();
		});
	}

	@Test
	void 빈_질문은_거부한다() throws Exception {
		mockMvc.perform(post("/demo/chat").contentType(MediaType.APPLICATION_JSON).content("""
				{"message": "   "}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("질문을 입력해주세요"));

		assertThat(requestLogRepository.count()).isZero();
	}

	@Test
	void 너무_긴_질문은_거부한다() throws Exception {
		String longMessage = "가".repeat(2001);
		mockMvc.perform(post("/demo/chat").contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\": \"" + longMessage + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 체험_페이지에_현재_모드를_표시한다() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("가짜 응답 모드")));
	}

}
