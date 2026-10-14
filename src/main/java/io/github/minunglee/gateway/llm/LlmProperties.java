package io.github.minunglee.gateway.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.llm")
public record LlmProperties(Mode mode, long maxTokens, Duration mockLatency, String anthropicApiKey) {

	public enum Mode {
		MOCK, ANTHROPIC
	}

}
