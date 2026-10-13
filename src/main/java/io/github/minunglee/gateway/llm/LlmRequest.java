package io.github.minunglee.gateway.llm;

public record LlmRequest(String model, String userMessage, long maxTokens) {
}
