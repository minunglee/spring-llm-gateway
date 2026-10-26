package io.github.minunglee.gateway.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class LlmClientConfig {

	/** 설정(gateway.llm.mode)에 따라 가짜 응답 또는 실제 Claude 클라이언트를 만든다. 실제 모드인데 API 키가 없으면 시작을 막는다 */
	@Bean
	LlmClient llmClient(LlmProperties properties) {
		if (properties.mode() == LlmProperties.Mode.MOCK) {
			return new MockLlmClient(properties.mockLatency());
		}
		if (!StringUtils.hasText(properties.anthropicApiKey())) {
			throw new IllegalStateException("anthropic 모드에는 ANTHROPIC_API_KEY가 필요합니다");
		}
		return new AnthropicLlmClient(AnthropicOkHttpClient.builder().apiKey(properties.anthropicApiKey()).build());
	}

}
