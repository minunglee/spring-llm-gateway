package io.github.minunglee.gateway.llm;

public record LlmResponse(String text, String model, long inputTokens, long outputTokens) {
}
